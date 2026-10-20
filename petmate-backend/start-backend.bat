@echo off
REM ===== 启动 PetMate 后端（Windows 沙箱用）=====
REM 关键坑：沙箱 cmd 下，批处理里 set 的环境变量【不会】传递给子进程 java.exe
REM （实测连 JAVA_HOME 都传不进去，但命令行上的 -Dserver.port 能传进去）。
REM 因此改为：先把 .env 读进 cmd 变量，再作为 -D 系统属性拼到 java 命令行上——
REM 系统属性一定能被 Spring 解析（yml 用 ${ALIPAY_ENABLED:false} 等占位符，
REM 系统属性与环境变量同名即可），从而让支付宝脱离演示态、能跳收银台。
set JAVA_HOME=D:\jdk21
cd /d "D:\PetMate\petmate-backend"

if exist "D:\PetMate\petmate-backend\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("D:\PetMate\petmate-backend\.env") do (
    set "%%A=%%B"
  )
)

"D:\jdk21\bin\java.exe" -Dserver.port=8080 ^
  -DALIPAY_ENABLED=%ALIPAY_ENABLED% ^
  -DALIPAY_APP_ID=%ALIPAY_APP_ID% ^
  -DALIPAY_PRIVATE_KEY=%ALIPAY_PRIVATE_KEY% ^
  -DALIPAY_PUBLIC_KEY=%ALIPAY_PUBLIC_KEY% ^
  -DALIPAY_GATEWAY=%ALIPAY_GATEWAY% ^
  -DALIPAY_NOTIFY_URL=%ALIPAY_NOTIFY_URL% ^
  -DALIPAY_RETURN_URL=%ALIPAY_RETURN_URL% ^
  -DZHIPU_API_KEY=%ZHIPU_API_KEY% ^
  -DPETMATE_JWT_SECRET=%PETMATE_JWT_SECRET% ^
  -jar "D:\PetMate\petmate-backend\target\petmate-backend.jar" >> "C:\Users\bloted\.workbuddy\logs\backend-detached.log" 2>&1
