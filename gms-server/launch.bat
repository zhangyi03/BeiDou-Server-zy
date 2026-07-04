@echo off
@title BeiDou
chcp 65001

java -Dspring.config.location=.\src\main\resources\application.yml -jar .\target\BeiDou.jar
pause