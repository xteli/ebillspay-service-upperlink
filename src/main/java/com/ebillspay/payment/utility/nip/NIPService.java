package com.ebillspay.payment.utility.nip;

import com.ebillspay.payment.lib.dto.helper.FTCreditRequest;
import com.ebillspay.payment.lib.dto.helper.FTCreditResponse;
import com.ebillspay.payment.lib.dto.helper.FTDebitRequest;
import com.ebillspay.payment.lib.dto.helper.FTDebitResponse;
import com.nibss.nip.NipException;
import com.nibss.nip.util.RandomGenerator;
import com.ebillspay.payment.lib.entities.Transaction;
import com.ebillspay.payment.lib.util.ResponseCode;
import com.ebillspay.payment.utility.util.PaymentUtil;
import com.nibss.nip.NipService;
import com.nibss.nip.crypto.FileSSMCipher;
import com.nibss.nip.impl.JaxwsNipService;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NIPService {

    private String nibssCode;
    private String nipUrl;
    private int channelCode;
    private String keyDir;
    private String password;
    private String organizationCode;

    public NIPService() {

    }

    public NIPService(String nipUrl, String nibssCode) {
        this.nipUrl = nipUrl;
        this.nibssCode = nibssCode;
    }

    public NIPService(String nibssCode, int channelCode) {
        this.nibssCode = nibssCode;
        this.channelCode = channelCode;
    }

    public NIPService(String nipUrl, String nibssCode, int channelCode) {
        this.nipUrl = nipUrl;
        this.nibssCode = nibssCode;
        this.channelCode = channelCode;
    }

    public NIPService(String nipUrl, String nibssCode, int channelCode, String keyDir, String password) {
        this.nipUrl = nipUrl;
        this.nibssCode = nibssCode;
        this.channelCode = channelCode;
        this.keyDir = keyDir;
        this.password = password;
    }

    //constructor for the new implementation
    public NIPService(String nipUrl, String organizationCode, String password, int channelCode) {
        this.nipUrl = nipUrl;
        this.organizationCode = organizationCode;
        this.password = password;
        this.channelCode = channelCode;
    }

    protected com.nibss.nip.dto.NESingleRequest convertToNameEnquiry(String accountNumber, String institutionCode) {
        com.nibss.nip.dto.NESingleRequest request = new com.nibss.nip.dto.NESingleRequest();
        if (accountNumber != null || !"".equals(accountNumber)) {
            request.setAccountNumber(accountNumber);
            request.setDestinationInstitutionCode(institutionCode);
            request.setChannelCode(channelCode);
            request.setSessionID(nibssCode + generateSessionID());
        }
        return request;
    }

    protected FTDebitRequest convertToFTDebit(Transaction deb) {
        FTDebitRequest request = new FTDebitRequest();
        if (deb != null) {
            request.setBeneficiaryAccountName(deb.getBeneficiaryAccountName());
            request.setBeneficiaryAccountNumber(deb.getBeneficiaryAccountNumber());
            request.setBeneficiaryBankVerificationNumber(toValue(deb.getBeneficiaryBvn()));
            request.setBeneficiaryKYCLevel(deb.getBeneficiaryKyc());
            request.setDestinationInstitutionCode(deb.getSrcBankCode());
            request.setNameEnquiryRef(deb.getSessionID());
            request.setDebitAccountName(deb.getSrcAccountName());
            request.setDebitAccountNumber(deb.getSrcAccountNumber());
            request.setDebitBankVerificationNumber(toValue(deb.getSrcBvn()));
            request.setDebitKYCLevel(deb.getSrcKyc());
            request.setMandateReferenceNumber(deb.getMandateRef());
            request.setTransactionFee(BigDecimal.ZERO);
            request.setAmount(deb.getDebitAmount());
            request.setChannelCode(channelCode);
            request.setNarration(toValue(deb.getNarration()));
            request.setPaymentReference(deb.getPaymentRef());
            request.setSessionID(deb.getSessionID());
            request.setTransactionLocation("Upperlink HQ");
        }
        return request;
    }

    protected FTCreditRequest convertToFTCredit(Transaction creditTransaction, boolean isFee) {
        FTCreditRequest creditRequest = new FTCreditRequest();
        if (creditTransaction != null) {
            creditRequest.setBeneficiaryAccountName(isFee ? creditTransaction.getFeeBeneficiaryAccountName() : creditTransaction.getBeneficiaryAccountName());
            creditRequest.setBeneficiaryAccountNumber(isFee ? creditTransaction.getFeeBeneficiaryAccountNumber() : creditTransaction.getBeneficiaryAccountNumber());
            creditRequest.setBeneficiaryKYCLevel(isFee ? creditTransaction.getFeeBeneficiaryKyc() : creditTransaction.getBeneficiaryKyc());
            creditRequest.setBeneficiaryBankVerificationNumber(isFee ? toValue(creditTransaction.getFeeBeneficiaryBvn()) : toValue(creditTransaction.getBeneficiaryBvn()));
            creditRequest.setDestinationInstitutionCode(isFee ? creditTransaction.getFeeBeneficiaryBankCode() : creditTransaction.getBeneficiaryBankCode());
            creditRequest.setOriginatorAccountName(creditTransaction.getSrcAccountName());
            creditRequest.setOriginatorAccountNumber(creditTransaction.getSrcAccountNumber());
            creditRequest.setOriginatorKYCLevel(creditTransaction.getSrcKyc());
            creditRequest.setOriginatorBankVerificationNumber(toValue(creditTransaction.getSrcBvn()));
            creditRequest.setNarration(toValue(creditTransaction.getNarration()));
            creditRequest.setPaymentReference(creditTransaction.getPaymentRef());
            creditRequest.setAmount(isFee ? creditTransaction.getFeeAmount() : creditTransaction.getCreditAmount());
            creditRequest.setChannelCode(channelCode);
            creditRequest.setSessionID(creditTransaction.getSessionID());
        }
        return creditRequest;
    }

    //NIP processes
    public com.nibss.nip.dto.NESingleResponse doNameEnquiry(String accountNumber, String institutionCode) throws NipException {
        try {
            if (nipUrl == null) {
                return null;
            }
            com.nibss.nip.dto.NESingleResponse res = createNipService().doNameEnquiry(convertToNameEnquiry(accountNumber, institutionCode));
            return res;
        } catch (MalformedURLException ex) {
            throw new NipException(ex);
        }
    }

    private NipService createNipService() throws MalformedURLException {
        return new JaxwsNipService(nipUrl, new FileSSMCipher(nibssCode, keyDir, password, nibssCode));
    }

    public String doNipDebit(Transaction debitTrans) {
        if (nipUrl == null || debitTrans == null) {
            return null;
        }
        try {
            FTDebitResponse debitResponse = new PaymentUtil(nipUrl).doNIPDebit(convertToFTDebit(debitTrans));
            return debitResponse != null ? debitResponse.getResponseCode() : ResponseCode.REQUEST_IN_PROGRESS;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ResponseCode.REQUEST_IN_PROGRESS;
    }

    public String doNipCredit(Transaction creditTrans, boolean isFee) {

        if (nipUrl == null || creditTrans == null) {
            return null;
        }
        try {
            FTCreditResponse response = new PaymentUtil(nipUrl).doNIPCredit(convertToFTCredit(creditTrans, isFee));
            return response != null ? response.getResponseCode() : ResponseCode.REQUEST_IN_PROGRESS;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ResponseCode.REQUEST_IN_PROGRESS;
    }

    private Integer toInt(String value) {
        try {
            return (value != null) ? Integer.valueOf(value) : 0;
        } catch (Exception e) {

        }
        return 0;
    }

    private String toValue(String value) {
        return value == null ? "" : value;
    }

    private String toValue(int value) {
        return value == 0 ? "" : String.valueOf(value);
    }

    private String generateSessionID() {
        return new SimpleDateFormat("yyMMddHHmmss").format(new Date()) + new RandomGenerator().getRandomLong(99999999999D, 999999999999D);
    }

}
