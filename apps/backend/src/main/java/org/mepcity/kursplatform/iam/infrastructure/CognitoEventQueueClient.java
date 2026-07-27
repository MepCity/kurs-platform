package org.mepcity.kursplatform.iam.infrastructure;

import java.util.List;

/** Transport adapter boundary. Receipt handles and SQS SDK types never leave infrastructure. */
public interface CognitoEventQueueClient {
    List<Delivery> receive(int limit);
    void acknowledge(String deliveryHandle);
    void retry(String deliveryHandle);
    void deadLetter(String deliveryHandle);

    record Delivery(String deliveryHandle, String body, int receiveCount) { }
}
