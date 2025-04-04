FROM openjdk:17-oracle

ENV S3_BUCKET_NAME=""
ENV S3_BUCKET_PREFIX=""
ENV AWS_REGION=""

COPY target/nucleus-tools-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]