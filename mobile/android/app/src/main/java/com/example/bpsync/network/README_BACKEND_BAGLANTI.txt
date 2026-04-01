BPSync - Backend (GitLab) Bağlantı
====================================

Bu klasör, pushlanan FastAPI backend'e bağlanmak için eklendi.

KULLANIM:
---------
1. Login sonrası token'ı saklayın:
   AuthTokenProvider.setToken(response.body()?.token)

2. API çağrıları:
   val api = RetrofitClient.api
   api.getDashboardSummary()
   api.getReadings()
   api.predictBp(BPPredictionRequest(ptt = 0.25, heart_rate = 72.0))

3. Base URL (emülatör): http://10.0.2.2:8000/
   Gerçek cihaz: ApiConstants.kt içinde BASE_URL'i bilgisayar IP'nizle değiştirin.

GEREKLİ BAĞIMLILIKLAR (app/build.gradle.kts):
---------------------------------------------
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

Backend'i çalıştırın: cd backend && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
