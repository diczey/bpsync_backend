"""
BLE Manager — BLE Connection Manager for BPSync Wrist Module

This service uses the `bleak` library to:
  1. Scan for a BLE device named `BPSync-Wrist`
  2. Connect automatically
  3. Listen for incoming JSON frames from the DataChar (Notify) characteristic
  4. Forward each frame to DataManager
  5. Reconnect automatically if the connection drops

BLE UUIDs match the firmware wrist_module.ino exactly:
  WRIST_SERVICE_UUID : 19B10000-E8F2-537E-4F6C-D104768A1214
  DATA_CHAR_UUID     : 19B10001-E8F2-537E-4F6C-D104768A1214  (Notify)
  CMD_CHAR_UUID      : 19B10002-E8F2-537E-4F6C-D104768A1214  (Write)

INSTALLATION REQUIREMENT:
  pip install bleak>=0.21.0
  (recommended to add to requirements.txt — see integration note)
"""

import asyncio
import logging
from datetime import datetime, timezone
from typing import Optional

logger = logging.getLogger(__name__)

# bleak import — optional dependency; BLEManager is disabled if not installed
try:
    from bleak import BleakScanner, BleakClient
    from bleak.backends.device import BLEDevice
    BLEAK_AVAILABLE = True
except ImportError:
    BLEAK_AVAILABLE = False
    logger.warning(
        "bleak library not found. BLEManager will run in passive mode. "
        "Install with: pip install bleak>=0.21.0"
    )


# ──────────────────────────────────────────────────────────────────────────────
#  UUID CONSTANTS  — must match firmware wrist_module.ino
# ──────────────────────────────────────────────────────────────────────────────
WRIST_DEVICE_NAME = "BPSync-Wrist"
WRIST_SERVICE_UUID = "19B10000-E8F2-537E-4F6C-D104768A1214"
DATA_CHAR_UUID     = "19B10001-E8F2-537E-4F6C-D104768A1214"  # Notify (read)
CMD_CHAR_UUID      = "19B10002-E8F2-537E-4F6C-D104768A1214"  # Write  (command)

# Firmware commands
CMD_START = b"START"
CMD_STOP  = b"STOP"

# Reconnection wait time (seconds)
RECONNECT_DELAY_S = 5
# Scan timeout (seconds)
SCAN_TIMEOUT_S = 20


# ──────────────────────────────────────────────────────────────────────────────
#  BLE MANAGER
# ──────────────────────────────────────────────────────────────────────────────
class BLEManager:
    """
    Asyncio-based BLE manager that connects to the BPSync Wrist module.

    Lifecycle:
        ble = BLEManager(data_manager=dm)
        await ble.start()   # starts background task — can be added to FastAPI lifespan
        ...
        await ble.stop()    # closes the connection

    Args:
        data_manager: The DataManager instance to process frames.
        device_name: BLE device name to scan for (default: "BPSync-Wrist").
        scan_timeout: Device discovery timeout in seconds (default: 10).
        user_id: User ID associated with this connection (forwarded to DataManager).
    """

    def __init__(
        self,
        data_manager,                    # DataManager (using Any to avoid circular import)
        device_name: str = WRIST_DEVICE_NAME,
        scan_timeout: int = SCAN_TIMEOUT_S,
        user_id: str = "",
    ) -> None:
        self._dm = data_manager
        self._device_name = device_name
        self._scan_timeout = scan_timeout
        self._user_id = user_id

        # State
        self._client: Optional["BleakClient"] = None
        self._connected: bool = False
        self._running: bool = False
        self._task: Optional[asyncio.Task] = None
        self._connected_at: Optional[datetime] = None
        self._device_address: Optional[str] = None

    # ── Public API ────────────────────────────────────────────────────────────

    async def start(self) -> None:
        """Starts the BLE loop in the background."""
        if not BLEAK_AVAILABLE:
            logger.error("BLEManager could not start: bleak is not installed.")
            return
        if self._running:
            logger.warning("BLEManager is already running.")
            return

        self._running = True
        self._task = asyncio.create_task(self._connection_loop(), name="ble_manager")
        logger.info("BLEManager started.")

    async def stop(self) -> None:
        """Stops the BLE loop and closes the active connection."""
        self._running = False
        if self._client and self._connected:
            try:
                await self._client.disconnect()
                logger.info("BLE connection closed.")
            except Exception as exc:
                logger.warning("Error closing connection: %s", exc)

        if self._task and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass

        self._connected = False
        logger.info("BLEManager stopped.")

    async def send_command(self, command: str) -> bool:
        """
        Sends a command to the wrist module.

        Args:
            command: "START" or "STOP"

        Returns:
            True on success, False if not connected or an error occurred.
        """
        if not self._connected or not self._client:
            logger.warning("Command could not be sent — not connected.")
            return False

        try:
            payload = command.encode("utf-8")[:20]  # Firmware max 20 bytes
            await self._client.write_gatt_char(CMD_CHAR_UUID, payload)
            logger.info("Command sent: %s", command)
            return True
        except Exception as exc:
            logger.error("Error sending command: %s", exc)
            return False

    def get_status(self) -> dict:
        """
        Returns the current BLE status.
        Called by the GET /ble/status endpoint.
        """
        return {
            "available": BLEAK_AVAILABLE,
            "connected": self._connected,
            "device_name": self._device_name,
            "device_address": self._device_address,
            "connected_at": (
                self._connected_at.isoformat() if self._connected_at else None
            ),
            "data_stats": self._dm.get_stats() if self._dm else {},
        }

    # ── BLE Loop ──────────────────────────────────────────────────────────────

    async def _connection_loop(self) -> None:
        """
        Main connection loop.
        Scans until the device is found, connects, and retries on disconnect.
        """
        while self._running:
            try:
                device = await self._scan_for_device()
                if device is None:
                    # Device not found — wait and retry
                    logger.info(
                        "'%s' not found, retrying in %ds...",
                        self._device_name, RECONNECT_DELAY_S
                    )
                    await asyncio.sleep(RECONNECT_DELAY_S)
                    continue

                await self._connect_and_stream(device)

            except asyncio.CancelledError:
                break
            except Exception as exc:
                logger.error("Unexpected error in connection loop: %s", exc)
                await asyncio.sleep(RECONNECT_DELAY_S)

    async def _scan_for_device(self) -> Optional["BLEDevice"]:
        """
        Scans the BLE environment and returns the device matching WRIST_DEVICE_NAME.
        Returns None if not found.
        """
        logger.info("BLE scan starting (%ds)...", self._scan_timeout)
        devices = await BleakScanner.discover(timeout=self._scan_timeout)
        for d in devices:
            if d.name and self._device_name.lower() in d.name.lower():
                logger.info("'%s' found: %s", d.name, d.address)
                return d
        return None

    async def _connect_and_stream(self, device: "BLEDevice") -> None:
        """
        Connects to the device, sends the START command, and listens for notifications.
        Returns when the connection drops (the loop handles reconnection).
        """
        async with BleakClient(
            device,
            disconnected_callback=self._on_disconnect,
        ) as client:
            self._client = client
            self._connected = True
            self._connected_at = datetime.now(timezone.utc)
            self._device_address = device.address
            logger.info("Connection established: %s", device.address)

            # Send streaming start command to firmware
            await self.send_command("START")

            # Listen on DataChar (Notify)
            await client.start_notify(DATA_CHAR_UUID, self._on_notification)
            logger.info("Listening for notifications (stop with Ctrl+C or stop())...")

            # Wait until disconnected
            while self._running and self._connected:
                await asyncio.sleep(1.0)

            # Cleanup
            try:
                await client.stop_notify(DATA_CHAR_UUID)
                await self.send_command("STOP")
            except Exception:
                pass  # Ignore if connection is already dropped

        self._client = None
        self._connected = False
        logger.info("Connection closed / dropped.")

    # ── Callbacks ─────────────────────────────────────────────────────────────

    def _on_disconnect(self, client: "BleakClient") -> None:
        """BleakClient disconnect callback (non-async)."""
        self._connected = False
        logger.warning("BLE connection unexpectedly dropped: %s", client.address)

    async def _on_notification(self, sender: int, data: bytearray) -> None:
        """
        Triggered when a BLE Notify arrives from the firmware.

        Args:
            sender: Characteristic handle (integer) — typically unused.
            data: Raw bytes (firmware sends a UTF-8 JSON string).
        """
        try:
            json_str = data.decode("utf-8")
            await self._dm.process_frame(json_str, user_id=self._user_id or None)
        except UnicodeDecodeError as exc:
            logger.warning("BLE data decode error: %s", exc)
        except Exception as exc:
            logger.error("Error processing notification: %s", exc)


# ──────────────────────────────────────────────────────────────────────────────
#  SINGLETON FACTORY  (optional — for FastAPI dependency injection)
# ──────────────────────────────────────────────────────────────────────────────
_ble_manager_instance: Optional[BLEManager] = None


def get_ble_manager() -> Optional[BLEManager]:
    """
    Returns the current BLEManager singleton.
    Available after init_ble_manager() is called in the main.py lifespan function.
    """
    return _ble_manager_instance


def init_ble_manager(data_manager, user_id: str = "", device_name: str = WRIST_DEVICE_NAME) -> BLEManager:
    """
    Creates and returns the BLEManager singleton.

    Example usage (main.py lifespan):
        from app.services.ble_manager import init_ble_manager, get_ble_manager
        dm = DataManager(db_factory=SessionLocal, default_user_id=settings.ble_default_user_id)
        ble = init_ble_manager(dm, user_id=settings.ble_default_user_id)
        await ble.start()
    """
    global _ble_manager_instance
    _ble_manager_instance = BLEManager(
        data_manager=data_manager,
        device_name=device_name,
        user_id=user_id,
    )
    return _ble_manager_instance
