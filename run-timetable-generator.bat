@echo off
cd /d "%~dp0"
java --enable-native-access=ALL-UNNAMED -cp ".;sqlite-jdbc-3.53.4.0 (1).jar" TimetableGenerator
