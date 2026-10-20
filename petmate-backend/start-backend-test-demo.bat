@echo off
REM ===== 临时验证用后端（演示态，port 8081，不影响主实例 8080）=====
REM 仅用于本地验证 grantVip / openVip 逻辑，验证后请停止该进程。
set JAVA_HOME=D:\jdk21
cd /d "D:\PetMate\petmate-backend"

if exist "D:\PetMate\petmate-backend\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("D:\PetMate\petmate-backend\.env") do (
    set "%%A=%%B"
  )
)

"D:\jdk21\bin\java.exe" -Dserver.port=8081 ^
  -DALIPAY_ENABLED=false ^
  -DALIPAY_APP_ID=%ALIPAY_APP_ID% ^
  -DALIPAY_PRIVATE_KEY=%ALIPAY_PRIVATE_KEY% ^
  -DALIPAY_PUBLIC_KEY=%ALIPAY_PUBLIC_KEY% ^
  -DALIPAY_GATEWAY=%ALIPAY_GATEWAY% ^
  -DALIPAY_NOTIFY_URL=%ALIPAY_NOTIFY_URL% ^
  -DALIPAY_RETURN_URL=%ALIPAY_RETURN_URL% ^
  -DZHIPU_API_KEY=%ZHIPU_API_KEY% ^
  -DPETMATE_JWT_SECRET=%PETMATE_JWT_SECRET% ^
  -jar "D:\PetMate\petmate-backend\target\petmate-backend.jar" >> "C:\Users\bloted\.workbuddy\logs\backend-test-demo.log" 2>&1
