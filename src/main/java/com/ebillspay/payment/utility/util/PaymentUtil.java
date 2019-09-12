/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ebillspay.payment.utility.util;

import com.ebillspay.payment.lib.dao.UtilitySystem;
import com.ebillspay.payment.lib.dto.helper.FTCreditRequest;
import com.ebillspay.payment.lib.dto.helper.FTCreditResponse;
import com.ebillspay.payment.lib.dto.helper.FTDebitRequest;
import com.ebillspay.payment.lib.dto.helper.FTDebitResponse;
import com.ebillspay.payment.lib.entities.Credentials;
import com.ebillspay.payment.lib.util.NIBSSAESEncryption;
import com.ebillspay.payment.lib.util.Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

/**
 *
 * @author chineduojiteli
 */
public class PaymentUtil {

    ObjectMapper objectMapper = new ObjectMapper();
    String baseUrl;
    public Client client;
    public WebTarget resetTarget, loginTarget;
    Response webResponse = null;
    Util util = new Util();
    String organizationCode, aesIVFromReset, aesKeyFromReset, password;
    UtilitySystem utilitySystem = new UtilitySystem();

    public PaymentUtil() {
        client = ClientBuilder.newClient();
//        baseUrl="http://196.6.103.10:9988/";
//        organizationCode="00030";
    }

    public PaymentUtil(String baseUrl) {
        this();
        this.baseUrl = baseUrl;
        organizationCode = util.getParameter("organizationCode");
//        aesIVFromReset = util.getParameter("aesIVFromReset");
//        aesKeyFromReset = util.getParameter("aesKeyFromReset");
//        password = util.getParameter("passwordFromReset");

    }

    //   public static void main(String args[]) {
//        PaymentUtil pm = new PaymentUtil();
//        pm.reset();
    //     System.out.println(new Util().generateSha256("0003020191003BU~0vn1CaZwq9OL").getBytes());
    //  }
    public boolean reset() {
        System.out.println(" ..: Inside reset() :.. ");
        boolean resetSuccessful = false;
        try {
            String resetUrl = baseUrl + "Reset";
            Map<String, String> requestHeaders = new HashMap<>();
            String encOrgCode = Base64.getEncoder().encodeToString(organizationCode.getBytes());//Base64Converter.encode(organizationCode.getBytes());
            requestHeaders.put("OrganisationCode", encOrgCode);
            webResponse = Util.sendRequest(resetUrl, "", "POST", true, requestHeaders, false, false);
            if (webResponse != null && webResponse.getStatus() == 200) {
                Credentials cred = utilitySystem.retrieveCredentials(organizationCode);
                if (cred == null) {
                    cred = new Credentials();
                    cred.setDateCreated(new Date());
                    cred.setDateUpdated(new Date());
                    cred.setOrganisationCode(organizationCode);
                    cred.setSecretKey(webResponse.getHeaderString("apiKey"));
                    cred.setIvKey(webResponse.getHeaderString("ivkey"));
                    cred.setPassword(webResponse.getHeaderString("password"));
                    resetSuccessful = utilitySystem.saveEntity(cred);
                } else {
                    cred.setSecretKey(webResponse.getHeaderString("apiKey"));
                    cred.setIvKey(webResponse.getHeaderString("ivkey"));
                    cred.setPassword(webResponse.getHeaderString("password"));
                    cred.setDateUpdated(new Date());
                    resetSuccessful = utilitySystem.updateEntity(cred);
                }
                if (resetSuccessful) {
                    System.out.println(" ..: Credentials have been reset for organization code : " + organizationCode);
                } else {
                    System.out.println(" ..: Credentials reset failed for organization code : " + organizationCode);
                }
            } else {
                System.out.println(" ..: Credential Reset failed with error  : " + webResponse.getStatus());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        System.out.println(" ..: Leaving reset() :.. ");
        return resetSuccessful;
    }

    public FTDebitResponse doNIPDebit(FTDebitRequest debitRequest) {
        System.out.println(" ..: Inside doNIPDebit() :.. ");
        FTDebitResponse debitResponse = null;
        try {
            Credentials cred = utilitySystem.retrieveCredentials(organizationCode);
            String debitUrl = baseUrl + "nip/debit";
            Map<String, String> requestHeaders = new HashMap<>();
            requestHeaders.put("Content-Type", "application/xml");
            //security headers
            String authorization = (cred != null) ? Base64.getEncoder().encodeToString((organizationCode + ":" + cred.getPassword()).getBytes()) : "";
            requestHeaders.put("Authorization", authorization);
            String clearSignature = (cred != null) ? organizationCode + new SimpleDateFormat("yyyyMMdd").format(new Date()) + cred.getPassword() : "";
            System.out.println("Clear Signature for NIP Debit : " + clearSignature);
            String hashSignature = util.generateSha256(clearSignature);
            System.out.println("Hash Signature [ Hex] for NIP Debit : " + hashSignature);
//            String signature = util.sha256BytesToHex(hashSignature.getBytes());
//            System.out.println("Hex Signature for NIP Debit : " + signature);
            requestHeaders.put("SIGNATURE", hashSignature);
            requestHeaders.put("SIGNATURE_METH", "SHA256");
            String encOrgCode = Base64.getEncoder().encodeToString(organizationCode.getBytes());
            requestHeaders.put("OrganisationCode", encOrgCode);
            String xmlRequest = util.toXml(debitRequest);
            System.out.println("Clear XML Request to NIP FT Debit : " + xmlRequest);
            xmlRequest = (cred != null) ? NIBSSAESEncryption.encryptAES(xmlRequest, cred.getSecretKey(), cred.getIvKey()) : "";
            System.out.println("Encrypted XML Request to NIP FT Debit : " + xmlRequest);
            webResponse = Util.sendRequest(debitUrl, xmlRequest, "POST", true, requestHeaders, false, true);
            if (webResponse != null) {
                String resp = "";
                if (webResponse.getStatus() == 200) {
                    resp = (String) webResponse.readEntity(String.class);
                    System.out.println("Encrypted XML Response from NIP FT Debit : " + resp);
                    resp = (cred != null) ? NIBSSAESEncryption.decryptAES(resp, cred.getSecretKey(), cred.getIvKey()) : "";
                    System.out.println("Clear XML Response from NIP FT Debit : " + resp);
                    debitResponse = util.fromXml(FTDebitResponse.class, resp);
                } else {
                    System.out.println(" NIP FT Debit failed with error :  " + webResponse.getStatus());
                    resp = (String) webResponse.readEntity(String.class);
                    System.out.println(" Response from NIP FT Debit : " + resp);
                }
            } else {
                System.out.println("No response from NIP Debit Service");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        System.out.println(" ..: Leaving doNIPDebit() :.. ");
        return debitResponse;
    }

    public FTCreditResponse doNIPCredit(FTCreditRequest debitRequest) {
        System.out.println(" ..: Inside doNIPCredit() :.. ");
        FTCreditResponse creditResponse = null;
        try {
            Credentials cred = utilitySystem.retrieveCredentials(organizationCode);
            String debitUrl = baseUrl + "nip/credit";
            Map<String, String> requestHeaders = new HashMap<>();
            requestHeaders.put("Content-Type", "application/xml");
            //security headers
            String authorization = (cred != null) ? Base64.getEncoder().encodeToString((organizationCode + ":" + cred.getPassword()).getBytes()) : "";
            requestHeaders.put("Authorization", authorization);
            String clearSignature = (cred != null) ? organizationCode + new SimpleDateFormat("yyyyMMdd").format(new Date()) + cred.getPassword() : "";
            System.out.println("Clear Signature for NIP Credit : " + clearSignature);
            String hashSignature = util.generateSha256(clearSignature);
            System.out.println("Hash Signature [Hex] for NIP Credit : " + hashSignature);
//            String signature = util.sha256BytesToHex(hashSignature.getBytes());
//            System.out.println("Hex Signature for NIP Credit : " + signature);
            requestHeaders.put("SIGNATURE", hashSignature);
            requestHeaders.put("SIGNATURE_METH", "SHA256");
            String encOrgCode = Base64.getEncoder().encodeToString(organizationCode.getBytes());
            requestHeaders.put("OrganisationCode", encOrgCode);
            String xmlRequest = util.toXml(debitRequest);
            System.out.println("Clear XML Request to NIP FT Credit : " + xmlRequest);
            xmlRequest = (cred != null) ? NIBSSAESEncryption.encryptAES(xmlRequest, cred.getSecretKey(), cred.getIvKey()) : "";
            System.out.println("Encrypted XML Request to NIP FT Credit : " + xmlRequest);
            webResponse = Util.sendRequest(debitUrl, xmlRequest, "POST", true, requestHeaders, false, true);
            if (webResponse != null) {
                String resp = "";
                if (webResponse.getStatus() == 200) {
                    resp = (String) webResponse.readEntity(String.class);
                    System.out.println("Encrypted XML Response from NIP FT Credit : " + resp);
                    resp = (cred != null) ? NIBSSAESEncryption.decryptAES(resp, cred.getSecretKey(), cred.getIvKey()) : "";
                    System.out.println("Clear XML Response from NIP FT Credit : " + resp);
                    creditResponse = util.fromXml(FTCreditResponse.class, resp);
                } else {
                    System.out.println(" NIP FT Credit failed with error :  " + webResponse.getStatus());
                    resp = (String) webResponse.readEntity(String.class);
                    System.out.println(" Response from NIP FT Credit : " + resp);
                }
            } else {
                System.out.println("No response from NIP Credit Service");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        System.out.println(" ..: Leaving doNIPCredit() :.. ");
        return creditResponse;
    }
}
