@echo off
echo Generating SSL certificate for Java-Talk WebSocket server...

REM 删除现有的证书文件（如果存在）
if exist keystore.jks (
    echo Removing existing keystore.jks...
    del keystore.jks
)
if exist server.key (
    echo Removing existing server.key...
    del server.key
)
if exist server.crt (
    echo Removing existing server.crt...
    del server.crt
)
if exist server.p12 (
    echo Removing existing server.p12...
    del server.p12
)

echo.
echo Checking available tools...

REM 检查OpenSSL
where openssl >nul 2>&1
set OPENSSL_AVAILABLE=%ERRORLEVEL%

REM 检查keytool
where keytool >nul 2>&1
set KEYTOOL_AVAILABLE=%ERRORLEVEL%

if %OPENSSL_AVAILABLE% EQU 0 (
    echo OpenSSL found - using OpenSSL method
    goto :use_openssl
) else if %KEYTOOL_AVAILABLE% EQU 0 (
    echo keytool found - using keytool method
    goto :use_keytool
) else (
    echo Error: Neither OpenSSL nor keytool found.
    echo Please install OpenSSL or ensure Java is installed with JAVA_HOME set.
    pause
    exit /b 1
)

:use_openssl
echo.
echo Creating SSL certificate using OpenSSL...
echo Default password will be 'changeit'
echo.

REM 使用OpenSSL生成私钥
echo Generating private key...
openssl genrsa -out server.key 2048
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to generate private key
    goto :error
)

REM 生成自签名证书
echo Generating self-signed certificate...
openssl req -new -x509 -key server.key -out server.crt -days 365 -subj "/CN=localhost/OU=Java-Talk/O=Core/L=City/ST=State/C=CN"
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to generate certificate
    goto :error
)

REM 创建PKCS12格式的keystore
echo Creating PKCS12 keystore...
openssl pkcs12 -export -in server.crt -inkey server.key -out server.p12 -name websocket-server -passout pass:changeit
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to create PKCS12 keystore
    goto :error
)

REM 转换为JKS格式
echo Converting to JKS format...
keytool -importkeystore -deststorepass changeit -destkeypass changeit -destkeystore keystore.jks -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass changeit -alias websocket-server >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Warning: keytool not available for JKS conversion, keeping PKCS12 format
    copy server.p12 keystore.p12 >nul
    echo Note: Update ssl.keystore.path to keystore.p12 and ensure your Java version supports PKCS12
) else (
    echo JKS keystore created successfully
)

goto :success

:use_keytool
echo.
echo Creating new keystore with self-signed certificate using keytool...
echo Default password will be 'changeit'
echo.

REM 生成自签名证书
keytool -genkeypair ^
    -alias websocket-server ^
    -keyalg RSA ^
    -keysize 2048 ^
    -storetype JKS ^
    -keystore keystore.jks ^
    -storepass changeit ^
    -keypass changeit ^
    -validity 365 ^
    -dname "CN=localhost, OU=Java-Talk, O=Core, L=City, ST=State, C=CN"

if %ERRORLEVEL% NEQ 0 (
    goto :error
)

goto :success

:success
echo.
echo SSL certificate generated successfully!
if exist keystore.jks (
    echo File: keystore.jks ^(JKS format^)
)
if exist keystore.p12 (
    echo File: keystore.p12 ^(PKCS12 format^)
)
echo Password: changeit
echo.
echo To enable SSL in your server:
echo 1. Set server.ssl.enabled=true in application.properties
echo 2. Update ssl.keystore.path, ssl.keystore.password, and ssl.key.password if needed
echo 3. Start the server with SSL support
echo.
echo Generated files:
if exist server.key echo - server.key ^(private key^)
if exist server.crt echo - server.crt ^(certificate^)
if exist server.p12 echo - server.p12 ^(PKCS12 keystore^)
if exist keystore.jks echo - keystore.jks ^(JKS keystore^)
goto :end

:error
echo.
echo Error: Failed to generate SSL certificate
echo.

:end
pause
