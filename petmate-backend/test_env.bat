@echo off
if exist "D:\PetMate\petmate-backend\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("D:\PetMate\petmate-backend\.env") do (
    set "%%A=%%B"
  )
)
echo RESULT_ALIPAY_ENABLED=[%ALIPAY_ENABLED%]
echo RESULT_ALIPAY_APP_ID=[%ALIPAY_APP_ID%]
echo RESULT_ZHIPU=[%ZHIPU_API_KEY%]
echo RESULT_SERVER_PORT=[%SERVER_PORT%]
