@echo off
setlocal
set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2024.3\jbr"
set "MAVEN_CMD=C:\Users\Toan\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [CANH BAO] Khong tim thay JDK tai "%JAVA_HOME%", se thu dung java mac dinh trong PATH.
    set "JAVA_HOME="
)

echo Dang khoi chay Ung Dung May Ban Trai Cay (JavaFX + SQLite)...
"%MAVEN_CMD%" javafx:run
endlocal
