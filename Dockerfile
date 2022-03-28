FROM 802306197541.dkr.ecr.eu-central-1.amazonaws.com/openjdk11:latest
RUN apk update && apk upgrade

ARG JAR_FILE=target/product-aggregator-*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-Xms2G","-Xmx2G","-jar","/app.jar"]
