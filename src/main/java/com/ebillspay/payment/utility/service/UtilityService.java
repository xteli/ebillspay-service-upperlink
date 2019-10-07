/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ebillspay.payment.utility.service;

import com.nibss.nip.dto.NESingleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.glassfish.grizzly.http.server.Request;
import com.ebillspay.payment.lib.dao.UtilitySystem;
import com.ebillspay.payment.lib.entities.SystemAudit;
import com.ebillspay.payment.lib.entities.Ping;
import com.ebillspay.payment.lib.util.Util;
import com.ebillspay.payment.lib.util.ResponseCode;
import com.ebillspay.payment.lib.util.Enum.OperationName;
import com.ebillspay.payment.lib.util.Enum.Channel;
import com.ebillspay.payment.lib.dto.UtilityResponse;
import com.ebillspay.payment.lib.dto.UtilityRequest;
import com.ebillspay.payment.lib.entities.Credentials;
import com.ebillspay.payment.lib.entities.Institution;
import com.ebillspay.payment.lib.entities.Transaction;
import com.ebillspay.payment.utility.nip.NIPService;
import com.ebillspay.payment.utility.util.PaymentUtil;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;

/**
 *
 * @author chineduojiteli
 */
@Path("ebillspay/api")
public class UtilityService {

    ObjectMapper objectMapper = new ObjectMapper();
    UtilitySystem utilitySystem = new UtilitySystem();
    Util config = new Util();
    SystemAudit auditTrail = new SystemAudit();
    UtilityResponse utilityResponse = new UtilityResponse();
    String nibssCode = "", nipUrl = "", keyDir = "", password = "", encryptionKey = "";
    String paymentRefFormat = "";
    int poolSize = 0;
    ExecutorService executorService = null;
    boolean feeSaved = false, feeUpdated = false;
    NESingleResponse feeAcct = null;
    String feeResponse = null;
    String organizationCode = "", nipPaymentBaseUrl = "", passwordFromReset;
    Transaction transaction = null;
    NIPService nipFTService = null;

    public UtilityService() {
        try {
            //parameters for the new FT implementation
            nipPaymentBaseUrl = config.getParameter("nipPaymentBaseUrl");
//            organizationCode = config.getParameter("organizationCode");
//            passwordFromReset = config.getParameter("passwordFromReset");
            //parameters for the old FT implementation
            nipUrl = config.getParameter("nipUrl");
            nibssCode = config.getParameter("nibssCode");
            encryptionKey = config.getParameter("encryptionKey");
            password = new Util(encryptionKey).decryptData(config.getParameter("password"));
            keyDir = config.getParameter("keyDir");
            //EBILLSPAY/{sessionID}/{src}/{dest}
            paymentRefFormat = config.getParameter("paymentRefFormat");
            poolSize = Integer.parseInt(config.getParameter("poolSize"));
            executorService = Executors.newFixedThreadPool(poolSize);
        } catch (Exception ex) {
        }
    }

    @GET
    @Produces("application/json")
    @Path("/ping")
    public Response pingService(@Context Request requestContext) {
        String pingResponse = "";
        System.out.println(" ..: Inside pingService() :..");
        auditTrail.setOperationName(OperationName.Ping.name());
        auditTrail.setOperationDate(new Date());
        String responseCode = "", responseDescription = "";
        try {
            String clearReq = "{\"request\":\"ping\"}";
            System.out.println("JSON Request (Clear) for Pinging  Service : " + clearReq);
            auditTrail.setPlainRequest(clearReq);
            System.out.println("About Pinging  Service ");
            Ping serviceAvailable = utilitySystem.isServiceAvailable();
            if (serviceAvailable != null) {
                responseCode = serviceAvailable.getStatus();
                responseDescription = serviceAvailable.getMessage();
            } else {
                responseCode = ResponseCode.SERVICE_NOT_AVAILABLE;
                responseDescription = "SERVICE NOT AVAILABLE";
            }
        } catch (Exception ex) {
            responseCode = ResponseCode.GENERAL_EXCEPTION;
            responseDescription = "An Error has Occurred";
            System.err.println("Error occurred in pingService() : " + ex.getMessage());
        } finally {
            utilityResponse.setResponseCode(responseCode);
            utilityResponse.setResponseDescription(responseDescription);
            try {
                pingResponse = objectMapper.writeValueAsString(utilityResponse);
                System.out.println("Ping Response : " + pingResponse);
                auditTrail.setPlainResponse(pingResponse);
                String remoteAddr = "";
                if (requestContext != null) {
                    remoteAddr = requestContext.getHeader("X-FORWARDED-FOR");
                    if (remoteAddr == null || "".equals(remoteAddr)) {
                        remoteAddr = requestContext.getRemoteAddr();
                    }
                }
                auditTrail.setIpAddress(remoteAddr);
                auditTrail.setStatusCode(responseCode);
                auditTrail.setStatusMessage(responseDescription);
                utilitySystem.saveEntity(auditTrail);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Response.ok(pingResponse).build();
    }

    @GET
    @Produces("application/json")
    @Path("/reset")
    public Response reset(@Context Request requestContext) {
        System.out.println(" ..: Inside reset() :..");
        auditTrail.setOperationDate(new Date());
        String initResponse = "";
        String status = "", message = "";
        Credentials cred = null;
        try {
            auditTrail.setOperationName(OperationName.Reset.name());
            PaymentUtil paymentUtil = new PaymentUtil(nipPaymentBaseUrl);
            System.out.println(" ..: About reseting credentials for Organization Code ");
            boolean done = paymentUtil.reset();
            if (done) {
                status = ResponseCode.SUCCESSFUL;
                message = "NIP PAYMENT SERVICE CREDENTIALS CHANGED SUCCESSFUL.PLEASE CHECK YOUR EMAIL AND MAINTAIN CREDENTIALS IN THE CONFIG";
            } else {
                status = ResponseCode.FAILED;
                message = "NIP PAYMENT SERVICE CREDENTIALS CHANGE FAILED";
            }

        } catch (Exception ex) {
            status = ResponseCode.GENERAL_EXCEPTION;
            message = "An Error has Occurred : " + ex.getMessage();
            ex.printStackTrace();
        } finally {
            utilityResponse.setResponseCode(message);
            utilityResponse.setResponseDescription(status);
            try {
                initResponse = objectMapper.writeValueAsString(utilityResponse);
                System.out.println("Reset Response : " + initResponse);
                auditTrail.setPlainResponse(initResponse);
                String remoteAddr = "";
                if (requestContext != null) {
                    remoteAddr = requestContext.getHeader("X-FORWARDED-FOR");
                    if (remoteAddr == null || "".equals(remoteAddr)) {
                        remoteAddr = requestContext.getRemoteAddr();
                    }
                }
                auditTrail.setIpAddress(remoteAddr);
                auditTrail.setStatusCode(status);
                auditTrail.setStatusMessage(message);
                utilitySystem.saveEntity(auditTrail);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println(" ..: Leaving reset() :..");
        return Response.ok(initResponse).build();
    }

    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/pay")
    public Response makePayment(@Context Request requestContext, String jsonRequest) {
        System.out.println(" ..: Inside makePayment() :..");
        auditTrail.setOperationDate(new Date());
        String response = "";
        String responseCode = "", responseDescription = "", otherInfo = null;
        NESingleResponse source = null, beneficiary = null;
        NIPService nipNEService = null;
        try {
            auditTrail.setOperationName(com.ebillspay.payment.lib.util.Enum.OperationName.MakePayment.name());
            System.out.println("JSON Request to Make Payment : " + jsonRequest);
            auditTrail.setPlainRequest(jsonRequest);
            System.out.println("About processing payment");
            UtilityRequest utilityRequest = Util.toPOJO(jsonRequest, UtilityRequest.class);
            if (utilityRequest != null) {
                if (Channel.toEnum(utilityRequest.getChannelCode()) == null) {
                    responseCode = ResponseCode.INVALID_CHANNEL;
                    responseDescription = "INVALID CHANNEL";
                } else if (utilityRequest.getSessionId() == null || "".equals(utilityRequest.getSessionId())) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "MISSING SESSION ID";
                } else if (utilityRequest.getSrcBankCode() == null || "".equals(utilityRequest.getSrcBankCode())
                        || utilityRequest.getSrcAcctNumber() == null || "".equals(utilityRequest.getSrcAcctNumber())
                        || utilityRequest.getDebitAmount() == null || "".equals(utilityRequest.getDebitAmount())) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "REQUIRED DEBIT PARAMETERS : SOURCE BANK CODE, SOURCE ACCOUNT NUMBER, DEBIT AMOUNT";
                } else if (utilityRequest.getBeneficiaryBankCode() == null || "".equals(utilityRequest.getBeneficiaryBankCode())
                        || utilityRequest.getBeneficiaryAcctNumber() == null || "".equals(utilityRequest.getBeneficiaryAcctNumber())
                        || utilityRequest.getCreditAmount() == null || "".equals(utilityRequest.getCreditAmount())) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "REQUIRED CREDIT PARAMETERS : DESTINATION BANK CODE, DESTINATION ACCOUNT NUMBER, CREDIT AMOUNT";
                } else if (utilityRequest.getFeeBeneficiaryBankCode() == null || "".equals(utilityRequest.getFeeBeneficiaryBankCode())
                        || utilityRequest.getFeeBeneficiaryAcctNumber() == null || "".equals(utilityRequest.getFeeBeneficiaryAcctNumber())
                        || utilityRequest.getFeeAmount() == null || "".equals(utilityRequest.getFeeAmount())) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "REQUIRED FEE PARAMETERS : DESTINATION BANK CODE, DESTINATION ACCOUNT NUMBER, FEE AMOUNT";
                } else if (new BigDecimal(utilityRequest.getDebitAmount()).compareTo((new BigDecimal(utilityRequest.getCreditAmount()).add(new BigDecimal(utilityRequest.getFeeAmount())))) != 0) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "DEBIT AMOUNT MUST EQUALS CREDIT AMOUNT AND FEE AMOUNT";
                } else if (utilitySystem.retrieveInstitution(utilityRequest.getSrcBankCode()) == null) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "INVALID SOURCE BANK";
                } else if (utilitySystem.retrieveInstitution(utilityRequest.getBeneficiaryBankCode()) == null) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "INVALID BENEFICIARY BANK";
                } else if (utilitySystem.retrieveInstitution(utilityRequest.getFeeBeneficiaryBankCode()) == null) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "INVALID FEE BENEFICIARY BANK";
                } else {
                    Institution srcInst = utilitySystem.retrieveInstitution(utilityRequest.getSrcBankCode());
                    //start nip payment
                    System.out.println("About to Make NIP Payment");
                    nipNEService = new NIPService(nipUrl, nibssCode, utilityRequest.getChannelCode(), keyDir, password);
                    source = nipNEService.doNameEnquiry(utilityRequest.getSrcAcctNumber(), srcInst.getInstitutionCode());
                    if (ResponseCode.SUCCESSFUL.equals(source.getResponseCode())) {
                        beneficiary = nipNEService.doNameEnquiry(utilityRequest.getBeneficiaryAcctNumber(), utilitySystem.retrieveInstitution(utilityRequest.getBeneficiaryBankCode()).getInstitutionCode());
                        if (ResponseCode.SUCCESSFUL.equals(beneficiary.getResponseCode())) {
                            nipFTService = new NIPService(nipPaymentBaseUrl, organizationCode, passwordFromReset, utilityRequest.getChannelCode());
                            //perform nip debit
                            transaction = new Transaction();
                            transaction.setRequestTime(new Date());
                            transaction.setDebitResponseCode(ResponseCode.REQUEST_IN_PROGRESS);
                            transaction.setChannel(utilityRequest.getChannelCode());
                            transaction.setSessionID(utilityRequest.getSessionId());
                            //source details
                            transaction.setDebitAmount(new BigDecimal(utilityRequest.getDebitAmount()));
                            transaction.setSrcAccountNumber(utilityRequest.getSrcAcctNumber());
                            transaction.setSrcAccountName(source.getAccountName());
                            transaction.setSrcBvn(source.getBankVerificationNumber());
                            transaction.setSrcKyc(source.getKYCLevel());
                            transaction.setSrcBankCode(utilityRequest.getSrcBankCode());
                            //beneficiary details
                            transaction.setBeneficiaryAccountName(beneficiary.getAccountName());
                            transaction.setBeneficiaryAccountNumber(beneficiary.getAccountNumber());
                            transaction.setBeneficiaryBankCode(utilityRequest.getBeneficiaryBankCode());
                            transaction.setBeneficiaryBvn(beneficiary.getBankVerificationNumber());
                            transaction.setBeneficiaryKyc(beneficiary.getKYCLevel());
                            transaction.setNarration("Payment from " + utilityRequest.getSrcAcctNumber() + " to " + beneficiary.getAccountNumber());

                            //EBILLSPAY/{sessionID}/{transType}/{accountNumber}
                            String debitPaymentRef = paymentRefFormat
                                    .replace("{sessionID}", transaction.getSessionID())
                                    .replace("{src}", transaction.getSrcAccountNumber())
                                    .replace("{dest}", transaction.getBeneficiaryAccountNumber());
                            transaction.setPaymentRef(debitPaymentRef);
                            System.out.println("About saving debit transaction details");
                            boolean debitSaved = utilitySystem.saveEntity(transaction);
                            if (debitSaved) {
                                //  debitTrans.setSrcBankCode(srcInst.getInstitutionCode());
                                transaction.setSrcBankCode(source.getDestinationInstitutionCode());
                                System.out.println("About debiting source account " + transaction.getSrcAccountNumber() + " with amount " + utilityRequest.getDebitAmount());
                                String debitResponse = nipFTService.doNipDebit(transaction, source.getSessionID());
                                transaction.setDebitResponseCode(debitResponse);
                                transaction.setResponseTime(new Date());
                                transaction.setSrcBankCode(utilityRequest.getSrcBankCode());
                                boolean debitUpdated = utilitySystem.updateEntity(transaction);
                                if (debitUpdated) {
                                    if (ResponseCode.SUCCESSFUL.equals(debitResponse)) {
                                        System.out.println("Debit Response : " + debitResponse);
                                        transaction.setCreditResponseCode(ResponseCode.REQUEST_IN_PROGRESS);
                                        transaction.setCreditAmount(new BigDecimal(utilityRequest.getCreditAmount()));
                                        System.out.println("About updating credit transaction details to beneficiary");
                                        boolean creditSaved = utilitySystem.updateEntity(transaction);
                                        if (creditSaved) {
                                            transaction.setBeneficiaryBankCode(beneficiary.getDestinationInstitutionCode());
                                            System.out.println("About crediting beneficiary account " + transaction.getBeneficiaryAccountNumber() + " with amount " + utilityRequest.getCreditAmount());
                                            String creditResponse = nipFTService.doNipCredit(transaction, false, beneficiary.getSessionID());
                                            transaction.setCreditResponseCode(creditResponse);
                                            transaction.setResponseTime(new Date());
                                            transaction.setBeneficiaryBankCode(utilityRequest.getBeneficiaryBankCode());
                                            boolean creditUpdated = utilitySystem.updateEntity(transaction);
                                            if (creditUpdated) {
                                                if (ResponseCode.SUCCESSFUL.equals(creditResponse)) {
                                                    System.out.println("Credit Response : " + creditResponse);
                                                    feeAcct = nipNEService.doNameEnquiry(utilityRequest.getFeeBeneficiaryAcctNumber(), utilitySystem.retrieveInstitution(utilityRequest.getFeeBeneficiaryBankCode()).getInstitutionCode());
                                                    transaction.setFeeResponseCode(ResponseCode.REQUEST_IN_PROGRESS);
                                                    transaction.setFeeAmount(new BigDecimal(utilityRequest.getFeeAmount()));
                                                    transaction.setFeeBeneficiaryBankCode(utilityRequest.getFeeBeneficiaryBankCode());
                                                    if (ResponseCode.SUCCESSFUL.equals(feeAcct.getResponseCode())) {
                                                        System.out.println("NE on Fee Account was successful");
                                                        //fee beneficiary details
                                                        transaction.setFeeBeneficiaryAccountName(feeAcct.getAccountName());
                                                        transaction.setFeeBeneficiaryAccountNumber(feeAcct.getAccountNumber());
                                                        transaction.setFeeBeneficiaryBvn(feeAcct.getBankVerificationNumber());
                                                        transaction.setFeeBeneficiaryKyc(feeAcct.getKYCLevel());

                                                        //delegate fee credit to an executor
                                                        Runnable feeThread = () -> {
                                                            feeSaved = utilitySystem.updateEntity(transaction);
                                                            if (feeSaved) {
                                                                transaction.setFeeBeneficiaryBankCode(feeAcct.getDestinationInstitutionCode());
                                                                feeResponse = nipFTService.doNipCredit(transaction, true, feeAcct.getSessionID());
                                                                transaction.setFeeResponseCode(feeResponse);
                                                                transaction.setFeeBeneficiaryBankCode(utilityRequest.getFeeBeneficiaryBankCode());
                                                                feeUpdated = utilitySystem.updateEntity(transaction);
                                                                if (!feeUpdated) {
//                                                            responseCode = ResponseCode.GENERAL_EXCEPTION;
//                                                            responseDescription = "PAYMENT [FEE] STATUS UPDATE ERROR";
                                                                }
                                                            } else {
                                                                //  responseCode = ResponseCode.GENERAL_EXCEPTION;
//                                                            responseDescription = "PAYMENT [FEE] INITIATION ERROR";
                                                            }
                                                        };

                                                        executorService.execute(feeThread);
                                                        responseCode = creditResponse;
                                                        responseDescription = "APPROVED BY FINANCIAL INSTITUTION";
                                                        otherInfo = transaction.getPaymentRef();
                                                    } else {
                                                        transaction.setFeeResponseCode(feeAcct.getResponseCode());
                                                        feeSaved = utilitySystem.updateEntity(transaction);
                                                        responseCode = feeAcct.getResponseCode();
                                                        responseDescription = "FEE BENEFICIARY BANK [" + utilityRequest.getFeeBeneficiaryBankCode() + "] NAME ENQUIRY ERROR";
                                                    }

                                                } else {
                                                    transaction.setRequiresReversal(true);
                                                    utilitySystem.updateEntity(transaction);
                                                    responseCode = creditResponse;
                                                    responseDescription = "NIP CREDIT FAILED WITH RESPONSE CODE " + creditResponse;
                                                }
                                            } else {
                                                responseCode = ResponseCode.GENERAL_EXCEPTION;
                                                responseDescription = "PAYMENT [CREDIT] STATUS UPDATE ERROR";
                                            }
                                        } else {
                                            responseCode = ResponseCode.GENERAL_EXCEPTION;
                                            responseDescription = "PAYMENT [CREDIT] INITIATION ERROR";
                                        }
                                    } else {
                                        responseCode = debitResponse;
                                        responseDescription = "NIP DEBIT FAILED WITH RESPONSE CODE " + debitResponse;
                                    }
                                } else {
                                    responseCode = ResponseCode.GENERAL_EXCEPTION;
                                    responseDescription = "PAYMENT [DEBIT] STATUS UPDATE ERROR";
                                }
                            } else {
                                responseCode = ResponseCode.GENERAL_EXCEPTION;
                                responseDescription = "PAYMENT [DEBIT] INITIATION ERROR";
                            }
                        } else {
                            responseCode = beneficiary.getResponseCode();
                            responseDescription = "BENEFICIARY BANK [" + utilityRequest.getBeneficiaryBankCode() + "] NAME ENQUIRY ERROR";
                        }
                    } else {
                        responseCode = source.getResponseCode();
                        responseDescription = "SOURCE BANK [" + utilityRequest.getSrcBankCode() + "] NAME ENQUIRY ERROR";
                    }
                }
            }

        } catch (Exception ex) {
            responseCode = ResponseCode.GENERAL_EXCEPTION;
            responseDescription = "An error occurred : " + ex.getMessage();
            ex.printStackTrace();
        } finally {
            utilityResponse.setResponseDescription(responseDescription);
            utilityResponse.setResponseCode(responseCode);
            utilityResponse.setOtherInfo(otherInfo);
            try {
                response = objectMapper.writeValueAsString(utilityResponse);
                System.out.println("Make Payment Response : " + response);
                auditTrail.setPlainResponse(response);

                String remoteAddr = "";
                if (requestContext != null) {
                    remoteAddr = requestContext.getHeader("X-FORWARDED-FOR");
                    if (remoteAddr == null || "".equals(remoteAddr)) {
                        remoteAddr = requestContext.getRemoteAddr();
                    }
                }
                auditTrail.setIpAddress(remoteAddr);
                auditTrail.setStatusCode(responseCode);
                auditTrail.setStatusMessage(responseDescription);
                utilitySystem.saveEntity(auditTrail);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Response.ok(response).build();
    }

    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/query")
    public Response query(@Context Request requestContext, String jsonRequest
    ) {
        System.out.println(" ..: Inside query() :..");
        auditTrail.setOperationDate(new Date());
        String queryResponse = "";
        String responseCode = "", responseDescription = "";
        List<Transaction> transList = null;
        Object data = null;
        try {
            System.out.println("JSON Request for Query Transaction : " + jsonRequest);
            auditTrail.setPlainRequest(jsonRequest);
            UtilityRequest utilityRequest = Util.toPOJO(jsonRequest, UtilityRequest.class
            );
            if (utilityRequest != null) {
                auditTrail.setOperationName(com.ebillspay.payment.lib.util.Enum.OperationName.Report.name());
                if (utilityRequest.getStartDate() == null || "".equals(utilityRequest.getStartDate())
                        || utilityRequest.getEndDate() == null || "".equals(utilityRequest.getEndDate())) {
                    responseCode = ResponseCode.INVALID_PARAMETERS;
                    responseDescription = "REQUIRED PARAMETERS : START DATE, END DATE";
                } else {
                    transList = utilitySystem.retrieveTransactions(utilityRequest.getStartDate(), utilityRequest.getEndDate(), utilityRequest.getSessionId(), utilityRequest.getSrcBankCode(), utilityRequest.getSrcAcctNumber(), utilityRequest.getPaymentRef());
                    if (transList != null && !transList.isEmpty()) {
                        responseCode = ResponseCode.SUCCESSFUL;
                        responseDescription = transList.size() + " TRANSACTION(S) FOUND";
                        data = transList;
                    } else {
                        responseCode = ResponseCode.NO_TRANSACTION_FOUND;
                        responseDescription = "NO TRANSACTION FOUND";
                    }
                }
            } else {
                responseCode = ResponseCode.JSON_MAPPING_EXCEPTION;
                responseDescription = "JSON MAPPING EXCEPTION";
            }
        } catch (Exception ex) {
            responseCode = ResponseCode.GENERAL_EXCEPTION;
            responseDescription = "An error occurred : " + ex.getMessage();
            ex.printStackTrace();
        } finally {
            utilityResponse.setResponseDescription(responseDescription);
            utilityResponse.setResponseCode(responseCode);
            utilityResponse.setData(data);
            try {
                queryResponse = objectMapper.writeValueAsString(utilityResponse);
                System.out.println("Transaction Query Response : " + queryResponse);
                auditTrail.setPlainResponse(queryResponse);
                String remoteAddr = "";
                if (requestContext != null) {
                    remoteAddr = requestContext.getHeader("X-FORWARDED-FOR");
                    if (remoteAddr == null || "".equals(remoteAddr)) {
                        remoteAddr = requestContext.getRemoteAddr();
                    }
                }
                auditTrail.setIpAddress(remoteAddr);
                auditTrail.setStatusCode(responseCode);
                auditTrail.setStatusMessage(responseDescription);
                utilitySystem.saveEntity(auditTrail);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Response.ok(queryResponse).build();

    }

}
