package gov.nasa.pds.nucleus.ingress;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.util.logging.Logger;

public class PDSNucleusS3BackLogProcessor {

    private static final Logger logger = Logger.getLogger(PDSNucleusS3BackLogProcessor.class.getName());

    public static void main(String[] args) {

        String s3BucketName = System.getenv("S3_BUCKET_NAME");
        String s3BucketPrefix =  System.getenv("S3_BUCKET_PREFIX");
        Region awsRegion = Region.of(System.getenv("AWS_REGION"));


        logger.info("s3BucketName = " + s3BucketName);
        logger.info("s3BucketPrefix = " + s3BucketPrefix);
        logger.info("Region = " + awsRegion.toString());
        logger.info("Processing S3 Backlog at: " + s3BucketPrefix);

        PDSNucleusS3BackLogProcessor pdsNucleusS3BackLogProcessor = new PDSNucleusS3BackLogProcessor();
        pdsNucleusS3BackLogProcessor.listObjectsInBucket(s3BucketName, s3BucketPrefix, awsRegion);

    }

    void listObjectsInBucket(String s3BucketName, String s3BucketPrefix, Region awsRegion) {
        S3Client s3Client = S3Client.builder()
                .region(awsRegion)
                .build();

        SqsClient sqsClient = SqsClient.builder()
                .region(awsRegion)
                .build();

        ListObjectsV2Request listObjectsReqManual = ListObjectsV2Request.builder()
                .bucket(s3BucketName)
                .prefix(s3BucketPrefix)
                .maxKeys(100)
                .build();

        boolean done = false;

        int pageNumber = 0;
        while (!done) {
            ListObjectsV2Response listObjResponse = s3Client.listObjectsV2(listObjectsReqManual);

            pageNumber++;
            System.out.println("Page size of page " + pageNumber + ": " + listObjResponse.contents().size());

            for (S3Object s3Object : listObjResponse.contents()) {
                System.out.println(s3Object.key());

                String event = "{\"backlog\":\"true\",\"s3_bucket\":\""+ s3BucketName + "\", \"s3_key\":\"" + s3Object.key() + "\"}";

                System.out.println("Sending event: " + event);

                sendMessage(sqsClient,
                        "https://sqs.us-west-2.amazonaws.com/441083951559/pds-nucleus-files-to-save-in-database-PDS_SBN", event);
            }

            if (listObjResponse.nextContinuationToken() == null) {
                done = true;
            }

            listObjectsReqManual = listObjectsReqManual.toBuilder()
                    .continuationToken(listObjResponse.nextContinuationToken())
                    .build();
        }



        s3Client.close();
    }

    void sendMessage(SqsClient sqsClient, String queueURL, String message) {
        try {

            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueURL)
                    .messageBody(message)
                    .delaySeconds(5)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}