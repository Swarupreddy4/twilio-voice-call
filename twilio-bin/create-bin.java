package com.example.twilio.bin;

import com.twilio.Twilio;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Java class to create and manage Twilio Bins programmatically
 * This can be used as an alternative to the shell scripts
 */
@Component
public class TwilioBinManager {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String phoneNumber;

    /**
     * Create a TwiML Bin via Twilio REST API
     * Note: Twilio Bins API requires direct HTTP calls as it's not in the Java SDK
     */
    public String createTwiMLBin(String webSocketUrl) {
        String twiML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response>" +
                "<Start>" +
                "<Stream url=\"" + webSocketUrl + "\" />" +
                "</Start>" +
                "<Say voice=\"alice\">Hello! I'm your AI assistant. How can I help you today?</Say>" +
                "<Pause length=\"30\" />" +
                "</Response>";

        // Use Twilio REST API to create bin
        // This requires making HTTP calls directly as the Java SDK doesn't include Bins API
        // You can use OkHttp, Apache HttpClient, or Spring's RestTemplate
        
        return twiML;
    }

    /**
     * Make an outbound call using the TwiML Bin
     */
    public void makeCall(String toNumber, String binSid) {
        Twilio.init(accountSid, authToken);
        
        Call call = Call.creator(
                new PhoneNumber(toNumber),
                new PhoneNumber(phoneNumber),
                URI.create("https://handler.twilio.com/" + binSid)
        ).create();

        System.out.println("Call SID: " + call.getSid());
    }
}

