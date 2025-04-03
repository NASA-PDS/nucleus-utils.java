// Copyright © 2025 , California Institute of Technology ("Caltech").
// U.S. Government sponsorship acknowledged.
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// • Redistributions of source code must retain the above copyright notice,
//   this list of conditions and the following disclaimer.
// • Redistributions must reproduce the above copyright notice, this list of
//   conditions and the following disclaimer in the documentation and/or other
//   materials provided with the distribution.
// • Neither the name of Caltech nor its operating division, the Jet Propulsion
//   Laboratory, nor the names of its contributors may be used to endorse or
//   promote products derived from this software without specific prior written
//   permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package gov.nasa.pds.nucleus.ingress;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is used to process files that have already been transferred to an S3 bucket,
 * and will not have an S3 trigger to start the baseline Nucleus DAG.
 * More details are available at <a href="https://github.com/NASA-PDS/nucleus/issues/141">...</a>
 *
 * @author The Planetary Data System
 * @since 2025
 */
public class PDSNucleusS3BackLogProcessor {

    private static final Logger logger = Logger.getLogger(PDSNucleusS3BackLogProcessor.class.getName());

    public static void main(String[] args) {

        String s3BucketName = System.getenv("S3_BUCKET_NAME");
        String s3BucketPrefix =  System.getenv("S3_BUCKET_PREFIX");
        String sqsQueueURL =  System.getenv("SQS_QUEUE_URL");
        Region awsRegion = Region.of(System.getenv("AWS_REGION"));

        logger.setLevel(Level.FINE);
        logger.info("s3BucketName = " + s3BucketName);
        logger.info("s3BucketPrefix = " + s3BucketPrefix);
        logger.info("Region = " + awsRegion.toString());
        logger.info("Processing S3 Backlog at: " + s3BucketPrefix);

        PDSNucleusS3BackLogProcessor pdsNucleusS3BackLogProcessor = new PDSNucleusS3BackLogProcessor();
        pdsNucleusS3BackLogProcessor.listObjectsInBucket(s3BucketName, s3BucketPrefix, sqsQueueURL, awsRegion);

    }

    /**
     * Lists objects in a given S3 bucket and sends messages to an SQS queue.
     * @param s3BucketName Name of the S3 bucket
     * @param s3BucketPrefix The prefix (path) in S3 bucket to start listing objects from
     * @param sqsQueueURL URL of the SQS Queue
     * @param awsRegion AWS Region
     */
    void listObjectsInBucket(String s3BucketName, String s3BucketPrefix, String sqsQueueURL, Region awsRegion) {
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

            logger.info("Page size of S3 object list: " + pageNumber + ": " + listObjResponse.contents().size());

            for (S3Object s3Object : listObjResponse.contents()) {
                String event = "{\"backlog\":\"true\",\"s3_bucket\":\""+ s3BucketName + "\", \"s3_key\":\"" + s3Object.key() + "\"}";
                logger.info("Sending event: " + event);
                sendMessage(sqsClient, sqsQueueURL, event);
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

    /**
     * Sends a message to a SQS queue.
     *
     * @param sqsClient SQS Client
     * @param queueURL SQS Queue URL
     * @param message Message
     */
    void sendMessage(SqsClient sqsClient, String queueURL, String message) {
        try {

            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueURL)
                    .messageBody(message)
                    .delaySeconds(5)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);

        } catch (Exception e) {
            logger.severe(e.toString());
            System.exit(1);
        }
    }
}