@echo off
REM ===== 临时验证用后端（真实支付路径 + 跳过验签，port 8082，不影响主实例 8080）=====
REM 目的：在沙箱网关偶发 504 且无法伪造支付宝签名时，本地验证
REM   openVip -> 插入 t_vip_pay pending -> 异步通知 -> grantVip -> status=paid 全链路。
REM 注意：v17 安全加固后，公钥未配置时 verifyNotify 直接拒绝（fail-closed）。
REM 因此本脚本必须同时传：
REM   -DALIPAY_PUBLIC_KEY=  留空 —— 仅用于本地 mock 通知链路，请勿用于真实支付
REM   -DALIPAY_INSECURE=true  跳过 TLS 校验（sandbox 偶发证书过期用）
REM 验证结束后请停止该进程。
set JAVA_HOME=D:\jdk21
cd /d "D:\PetMate\petmate-backend"

if exist "D:\PetMate\petmate-backend\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("D:\PetMate\petmate-backend\.env") do (
    set "%%A=%%B"
  )
)

"D:\jdk21\bin\java.exe" -Dserver.port=8082 ^
  -DALIPAY_ENABLED=true ^
  -DALIPAY_APP_ID=%ALIPAY_APP_ID% ^
  -DALIPAY_PRIVATE_KEY=%ALIPAY_PRIVATE_KEY% ^
  -DALIPAY_PUBLIC_KEY= ^
  -DALIPAY_GATEWAY=%ALIPAY_GATEWAY% ^
  -DALIPAY_NOTIFY_URL=%ALIPAY_NOTIFY_URL% ^
  -DALIPAY_RETURN_URL=%ALIPAY_RETURN_URL% ^
  -DZHIPU_API_KEY=%ZHIPU_API_KEY% ^
  -DPETMATE_JWT_SECRET=%PETMATE_JWT_SECRET% ^
  -DALIPAY_INSECURE=true ^
  -jar "D:\PetMate\petmate-backend\target\petmate-backend.jar" >> "C:\Users\bloted\.workbuddy\logs\backend-test-bypass.log" 2>&1
