## Database

See database setup guide: [database/README.md](database/README.md)

# BPSync Database (Local PostgreSQL Setup)

This folder contains the database setup for the BPSync project.

During development, each team member runs the database **locally** using Docker.
This ensures everyone uses the same PostgreSQL version and configuration, without
installing PostgreSQL manually on Windows.

> Note: Local databases are separate per developer.  
> We share the **schema and migrations** through GitLab, not the local data.

---

## Why do we use Docker for PostgreSQL?

- No manual PostgreSQL installation required
- Same PostgreSQL version for the whole team (`postgres:16`)
- Fast setup with a single command
- Easy reset for clean testing environments

---

## Prerequisites

- Docker Desktop installed and running
- Git installed
- Repository pulled from GitLab
