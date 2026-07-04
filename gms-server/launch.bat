@echo off
@title BeiDou
chcp 65001

..\..\jdk-21.0.1\bin\java.exe -Dspring.config.location=.\src\main\resources\application.yml -jar .\target\BeiDou.jar
pause