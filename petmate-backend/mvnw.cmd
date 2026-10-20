@echo off
setlocal
set DIR=%~dp0
java -classpath "%DIR%.mvn\wrapper\maven-wrapper.jar" -Dwrapper.properties="%DIR%.mvn\wrapper\maven-wrapper.properties" -Dmaven.multiModuleProjectDirectory=%DIR% org.apache.maven.wrapper.MavenWrapperMain %*
