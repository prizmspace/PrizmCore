/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package prizm.http;

import prizm.Account;
import prizm.Alias;
import prizm.Appendix;
import prizm.Constants;
import prizm.HoldingType;
import prizm.Prizm;
import prizm.PrizmException;
import prizm.Transaction;
import prizm.crypto.Crypto;
import prizm.crypto.EncryptedData;
import prizm.util.Convert;
import prizm.util.Logger;
import prizm.util.Search;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

import static prizm.http.JSONResponses.*;

public final class ParameterParser {

    public static byte getByte(HttpServletRequest req, String name, byte min, byte max, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            byte value = Byte.parseByte(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static int getInt(HttpServletRequest req, String name, int min, int max, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            int value = Integer.parseInt(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static long getLong(HttpServletRequest req, String name, long min, long max,
                        boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            long value = Long.parseLong(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static long getUnsignedLong(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            long value = Convert.parseUnsignedLong(paramValue);
            if (value == 0) { // 0 is not allowed as an id
                throw new ParameterException(incorrect(name));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
    }

    public static long[] getUnsignedLongs(HttpServletRequest req, String name) throws ParameterException {
        String[] paramValues = req.getParameterValues(name);
        if (paramValues == null || paramValues.length == 0) {
            throw new ParameterException(missing(name));
        }
        long[] values = new long[paramValues.length];
        try {
            for (int i = 0; i < paramValues.length; i++) {
                if (paramValues[i] == null || paramValues[i].isEmpty()) {
                    throw new ParameterException(incorrect(name));
                }
                values[i] = Long.parseUnsignedLong(paramValues[i]);
                if (values[i] == 0) {
                    throw new ParameterException(incorrect(name));
                }
            }
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
        return values;
    }

    public static byte[] getBytes(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return Convert.EMPTY_BYTE;
        }
        return Convert.parseHexString(paramValue);
    }

    public static long getAccountId(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        return getAccountId(req, "account", isMandatory);
    }

    public static long getAccountId(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            long value = Convert.parseAccountId(paramValue);
            if (value == 0) {
                throw new ParameterException(incorrect(name));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
    }

    public static long[] getAccountIds(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        String[] paramValues = req.getParameterValues("account");
        if (paramValues == null || paramValues.length == 0) {
            if (isMandatory) {
                throw new ParameterException(MISSING_ACCOUNT);
            } else {
                return Convert.EMPTY_LONG;
            }
        }
        long[] values = new long[paramValues.length];
        try {
            for (int i = 0; i < paramValues.length; i++) {
                if (paramValues[i] == null || paramValues[i].isEmpty()) {
                    throw new ParameterException(INCORRECT_ACCOUNT);
                }
                values[i] = Convert.parseAccountId(paramValues[i]);
                if (values[i] == 0) {
                    throw new ParameterException(INCORRECT_ACCOUNT);
                }
            }
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_ACCOUNT);
        }
        return values;
    }

    public static Alias getAlias(HttpServletRequest req) throws ParameterException {
        long aliasId;
        try {
            aliasId = Convert.parseUnsignedLong(Convert.emptyToNull(req.getParameter("alias")));
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_ALIAS);
        }
        String aliasName = Convert.emptyToNull(req.getParameter("aliasName"));
        Alias alias;
        if (aliasId != 0) {
            alias = Alias.getAlias(aliasId);
        } else if (aliasName != null) {
            alias = Alias.getAlias(aliasName);
        } else {
            throw new ParameterException(MISSING_ALIAS_OR_ALIAS_NAME);
        }
        if (alias == null) {
            throw new ParameterException(UNKNOWN_ALIAS);
        }
        return alias;
    }

    public static long getAmountNQT(HttpServletRequest req) throws ParameterException {
        return getLong(req, "amountNQT", 1L, Constants.MAX_BALANCE_NQT, true);
    }

    public static long getFeeNQT(HttpServletRequest req) throws ParameterException {
        return getLong(req, "feeNQT", 0L, Constants.MAX_BALANCE_NQT, true);
    }

    public static long getAmountNQTPerQNT(HttpServletRequest req) throws ParameterException {
        return getLong(req, "amountNQTPerQNT", 1L, Constants.MAX_BALANCE_NQT, true);
    }

    public static EncryptedData getEncryptedData(HttpServletRequest req, String messageType) throws ParameterException {
        String dataString = Convert.emptyToNull(req.getParameter(messageType + "Data"));
        String nonceString = Convert.emptyToNull(req.getParameter(messageType + "Nonce"));
        if (nonceString == null) {
            return null;
        }
        byte[] data;
        byte[] nonce;
        try {
            nonce = Convert.parseHexString(nonceString);
        } catch (RuntimeException e) {
            throw new ParameterException(JSONResponses.incorrect(messageType + "Nonce"));
        }
        if (dataString != null) {
            try {
                data = Convert.parseHexString(dataString);
            } catch (RuntimeException e) {
                throw new ParameterException(JSONResponses.incorrect(messageType + "Data"));
            }
        } else {
            if (req.getContentType() == null || !req.getContentType().startsWith("multipart/form-data")) {
                return null;
            }
            try {
                Part part = req.getPart(messageType + "File");
                if (part == null) {
                    return null;
                }
                FileData fileData = new FileData(part).invoke();
                data = fileData.getData();
            } catch (IOException | ServletException e) {
                Logger.logDebugMessage("error in reading file data", e);
                throw new ParameterException(JSONResponses.incorrect(messageType + "File"));
            }
        }
        return new EncryptedData(data, nonce);
    }

    public static Appendix.EncryptToSelfMessage getEncryptToSelfMessage(HttpServletRequest req) throws ParameterException {
        boolean isText = !"false".equalsIgnoreCase(req.getParameter("messageToEncryptToSelfIsText"));
        boolean compress = !"false".equalsIgnoreCase(req.getParameter("compressMessageToEncryptToSelf"));
        byte[] plainMessageBytes = null;
        EncryptedData encryptedData = ParameterParser.getEncryptedData(req, "encryptToSelfMessage");
        if (encryptedData == null) {
            String plainMessage = Convert.emptyToNull(req.getParameter("messageToEncryptToSelf"));
            if (plainMessage == null) {
                return null;
            }
            try {
                plainMessageBytes = isText ? Convert.toBytes(plainMessage) : Convert.parseHexString(plainMessage);
            } catch (RuntimeException e) {
                throw new ParameterException(INCORRECT_MESSAGE_TO_ENCRYPT);
            }
            String secretPhrase = getSecretPhrase(req, false);
            if (secretPhrase != null) {
                byte[] publicKey = Crypto.getPublicKey(secretPhrase);
                encryptedData = Account.encryptTo(publicKey, plainMessageBytes, secretPhrase, compress);
            }
        }
        if (encryptedData != null) {
            return new Appendix.EncryptToSelfMessage(encryptedData, isText, compress);
        } else {
            return new Appendix.UnencryptedEncryptToSelfMessage(plainMessageBytes, isText, compress);
        }
    }

    public static String getSecretPhrase(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        String secretPhrase = Convert.emptyToNull(req.getParameter("secretPhrase"));
        if (secretPhrase == null && isMandatory) {
            throw new ParameterException(MISSING_SECRET_PHRASE);
        }
        return secretPhrase;
    }

    public static byte[] getPublicKey(HttpServletRequest req) throws ParameterException {
        return getPublicKey(req, null);
    }

    public static byte[] getPublicKey(HttpServletRequest req, String prefix) throws ParameterException {
        String secretPhraseParam = prefix == null ? "secretPhrase" : (prefix + "SecretPhrase");
        String publicKeyParam = prefix == null ? "publicKey" : (prefix + "PublicKey");
        String secretPhrase = Convert.emptyToNull(req.getParameter(secretPhraseParam));
        if (secretPhrase == null) {
            try {
                byte[] publicKey = Convert.parseHexString(Convert.emptyToNull(req.getParameter(publicKeyParam)));
                if (publicKey == null) {
                    throw new ParameterException(missing(secretPhraseParam, publicKeyParam));
                }
                if (!Crypto.isCanonicalPublicKey(publicKey)) {
                    throw new ParameterException(incorrect(publicKeyParam));
                }
                return publicKey;
            } catch (RuntimeException e) {
                throw new ParameterException(incorrect(publicKeyParam));
            }
        } else {
            return Crypto.getPublicKey(secretPhrase);
        }
    }

    public static Account getSenderAccount(HttpServletRequest req) throws ParameterException {
        byte[] publicKey = getPublicKey(req);
        Account account = Account.getAccount(publicKey);
        if (account == null) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        return account;
    }

    public static Account getAccount(HttpServletRequest req) throws ParameterException {
        return getAccount(req, true);
    }

    public static Account getAccount(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        long accountId = getAccountId(req, "account", isMandatory);
        if (accountId == 0 && !isMandatory) {
            return null;
        }
        Account account = Account.getAccount(accountId);
        if (account == null) {
            throw new ParameterException(JSONResponses.unknownAccount(accountId));
        }
        return account;
    }

    public static List<Account> getAccounts(HttpServletRequest req) throws ParameterException {
        String[] accountValues = req.getParameterValues("account");
        if (accountValues == null || accountValues.length == 0) {
            throw new ParameterException(MISSING_ACCOUNT);
        }
        List<Account> result = new ArrayList<>();
        for (String accountValue : accountValues) {
            if (accountValue == null || accountValue.equals("")) {
                continue;
            }
            try {
                Account account = Account.getAccount(Convert.parseAccountId(accountValue));
                if (account == null) {
                    throw new ParameterException(UNKNOWN_ACCOUNT);
                }
                result.add(account);
            } catch (RuntimeException e) {
                throw new ParameterException(INCORRECT_ACCOUNT);
            }
        }
        return result;
    }

    public static int getTimestamp(HttpServletRequest req) throws ParameterException {
        return getInt(req, "timestamp", 0, Integer.MAX_VALUE, false);
    }

    public static int getFirstIndex(HttpServletRequest req) {
        try {
            int firstIndex = Integer.parseInt(req.getParameter("firstIndex"));
            if (firstIndex < 0) {
                return 0;
            }
            return firstIndex;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int getLastIndex(HttpServletRequest req) {
        int lastIndex = Integer.MAX_VALUE;
        try {
            lastIndex = Integer.parseInt(req.getParameter("lastIndex"));
            if (lastIndex < 0) {
                lastIndex = Integer.MAX_VALUE;
            }
        } catch (NumberFormatException ignored) {}
        if (!API.checkPassword(req)) {
            int firstIndex = Math.min(getFirstIndex(req), Integer.MAX_VALUE - API.maxRecords + 1);
            lastIndex = Math.min(lastIndex, firstIndex + API.maxRecords - 1);
        }
        return lastIndex;
    }

    public static int getNumberOfConfirmations(HttpServletRequest req) throws ParameterException {
        return getInt(req, "numberOfConfirmations", 0, Prizm.getBlockchain().getHeight(), false);
    }

    public static int getHeight(HttpServletRequest req) throws ParameterException {
        String heightValue = Convert.emptyToNull(req.getParameter("height"));
        if (heightValue != null) {
            try {
                int height = Integer.parseInt(heightValue);
                if (height < 0 || height > Prizm.getBlockchain().getHeight()) {
                    throw new ParameterException(INCORRECT_HEIGHT);
                }
                return height;
            } catch (NumberFormatException e) {
                throw new ParameterException(INCORRECT_HEIGHT);
            }
        }
        return -1;
    }

    public static HoldingType getHoldingType(HttpServletRequest req) throws ParameterException {
        return HoldingType.get(ParameterParser.getByte(req, "holdingType", (byte) 0, (byte) 2, false));
    }

    public static long getHoldingId(HttpServletRequest req, HoldingType holdingType) throws ParameterException {
        long holdingId = ParameterParser.getUnsignedLong(req, "holding", holdingType != HoldingType.PRIZM);
        if (holdingType == HoldingType.PRIZM && holdingId != 0) {
            throw new ParameterException(JSONResponses.incorrect("holding", "holding id should not be specified if holdingType is Coins"));
        }
        return holdingId;
    }

    public static String getSearchQuery(HttpServletRequest req) throws ParameterException {
        String query = Convert.nullToEmpty(req.getParameter("query")).trim();
        String tags = Convert.nullToEmpty(req.getParameter("tag")).trim();
        if (query.isEmpty() && tags.isEmpty()) {
            throw new ParameterException(JSONResponses.missing("query", "tag"));
        }
        if (!tags.isEmpty()) {
            StringJoiner stringJoiner = new StringJoiner(" AND TAGS:", "TAGS:", "");
            for (String tag : Search.parseTags(tags, 0, Integer.MAX_VALUE, Integer.MAX_VALUE)) {
                stringJoiner.add(tag);
            }
            query = stringJoiner.toString() + (query.isEmpty() ? "" : (" AND (" + query + ")"));
        }
        return query;
    }

    public static Transaction.Builder parseTransaction(String transactionJSON, String transactionBytes, String prunableAttachmentJSON) throws ParameterException {
        if (transactionBytes == null && transactionJSON == null) {
            throw new ParameterException(MISSING_TRANSACTION_BYTES_OR_JSON);
        }
        if (transactionBytes != null && transactionJSON != null) {
            throw new ParameterException(either("transactionBytes", "transactionJSON"));
        }
        if (prunableAttachmentJSON != null && transactionBytes == null) {
            throw new ParameterException(JSONResponses.missing("transactionBytes"));
        }
        if (transactionJSON != null) {
            try {
                JSONObject json = (JSONObject) JSONValue.parseWithException(transactionJSON);
                return Prizm.newTransactionBuilder(json);
            } catch (PrizmException.ValidationException | RuntimeException | ParseException e) {
                Logger.logDebugMessage(e.getMessage(), e);
                JSONObject response = new JSONObject();
                JSONData.putException(response, e, "Incorrect transactionJSON");
                throw new ParameterException(response);
            }
        } else {
            try {
                byte[] bytes = Convert.parseHexString(transactionBytes);
                JSONObject prunableAttachments = prunableAttachmentJSON == null ? null : (JSONObject)JSONValue.parseWithException(prunableAttachmentJSON);
                return Prizm.newTransactionBuilder(bytes, prunableAttachments);
            } catch (PrizmException.ValidationException|RuntimeException | ParseException e) {
                Logger.logDebugMessage(e.getMessage(), e);
                JSONObject response = new JSONObject();
                JSONData.putException(response, e, "Incorrect transactionBytes");
                throw new ParameterException(response);
            }
        }
    }

    public static Appendix getPlainMessage(HttpServletRequest req, boolean prunable) throws ParameterException {
        String messageValue = Convert.emptyToNull(req.getParameter("message"));
        boolean messageIsText = !"false".equalsIgnoreCase(req.getParameter("messageIsText"));
        if (messageValue != null) {
            try {
                if (prunable) {
                    return new Appendix.PrunablePlainMessage(messageValue, messageIsText);
                } else {
                    return new Appendix.Message(messageValue, messageIsText);
                }
            } catch (RuntimeException e) {
                throw new ParameterException(INCORRECT_ARBITRARY_MESSAGE);
            }
        }
        if (req.getContentType() == null || !req.getContentType().startsWith("multipart/form-data")) {
            return null;
        }
        try {
            Part part = req.getPart("messageFile");
            if (part == null) {
                return null;
            }
            FileData fileData = new FileData(part).invoke();
            byte[] message = fileData.getData();
            String detectedMimeType = Search.detectMimeType(message);
            if (detectedMimeType != null) {
                messageIsText = detectedMimeType.startsWith("text/");
            }
            if (messageIsText && !Arrays.equals(message, Convert.toBytes(Convert.toString(message)))) {
                messageIsText = false;
            }
            if (prunable) {
                return new Appendix.PrunablePlainMessage(message, messageIsText);
            } else {
                return new Appendix.Message(message, messageIsText);
            }
        } catch (IOException | ServletException e) {
            Logger.logDebugMessage("error in reading file data", e);
            throw new ParameterException(INCORRECT_ARBITRARY_MESSAGE);
        }
    }

    public static Appendix getEncryptedMessage(HttpServletRequest req, Account recipient, boolean prunable) throws ParameterException {
        boolean isText = !"false".equalsIgnoreCase(req.getParameter("messageToEncryptIsText"));
        boolean compress = !"false".equalsIgnoreCase(req.getParameter("compressMessageToEncrypt"));
        byte[] plainMessageBytes = null;
        byte[] recipientPublicKey = null;
        EncryptedData encryptedData = ParameterParser.getEncryptedData(req, "encryptedMessage");
        if (encryptedData == null) {
            String plainMessage = Convert.emptyToNull(req.getParameter("messageToEncrypt"));
            if (plainMessage == null) {
                if (req.getContentType() == null || !req.getContentType().startsWith("multipart/form-data")) {
                    return null;
                }
                try {
                    Part part = req.getPart("messageToEncryptFile");
                    if (part == null) {
                        return null;
                    }
                    FileData fileData = new FileData(part).invoke();
                    plainMessageBytes = fileData.getData();
                    String detectedMimeType = Search.detectMimeType(plainMessageBytes);
                    if (detectedMimeType != null) {
                        isText = detectedMimeType.startsWith("text/");
                    }
                    if (isText && !Arrays.equals(plainMessageBytes, Convert.toBytes(Convert.toString(plainMessageBytes)))) {
                        isText = false;
                    }
                } catch (IOException | ServletException e) {
                    Logger.logDebugMessage("error in reading file data", e);
                    throw new ParameterException(INCORRECT_MESSAGE_TO_ENCRYPT);
                }
            } else {
                try {
                    plainMessageBytes = isText ? Convert.toBytes(plainMessage) : Convert.parseHexString(plainMessage);
                } catch (RuntimeException e) {
                    throw new ParameterException(INCORRECT_MESSAGE_TO_ENCRYPT);
                }
            }
            if (recipient != null) {
                recipientPublicKey = Account.getPublicKey(recipient.getId());
            }
            if (recipientPublicKey == null) {
                recipientPublicKey = Convert.parseHexString(Convert.emptyToNull(req.getParameter("recipientPublicKey")));
            }
            if (recipientPublicKey == null) {
                throw new ParameterException(MISSING_RECIPIENT_PUBLIC_KEY);
            }
            String secretPhrase = getSecretPhrase(req, false);
            if (secretPhrase != null) {
                encryptedData = Account.encryptTo(recipientPublicKey, plainMessageBytes, secretPhrase, compress);
            }
        }
        if (encryptedData != null) {
            if (prunable) {
                return new Appendix.PrunableEncryptedMessage(encryptedData, isText, compress);
            } else {
                return new Appendix.EncryptedMessage(encryptedData, isText, compress);
            }
        } else {
            if (prunable) {
                return new Appendix.UnencryptedPrunableEncryptedMessage(plainMessageBytes, isText, compress, recipientPublicKey);
            } else {
                return new Appendix.UnencryptedEncryptedMessage(plainMessageBytes, isText, compress, recipientPublicKey);
            }
        }
    }

    private ParameterParser() {} // never

    public static class FileData {
        private final Part part;
        private String filename;
        private byte[] data;

        public FileData(Part part) {
            this.part = part;
        }

        public String getFilename() {
            return filename;
        }

        public byte[] getData() {
            return data;
        }

        public FileData invoke() throws IOException {
            try (InputStream is = part.getInputStream()) {
                int nRead;
                byte[] bytes = new byte[1024];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((nRead = is.read(bytes, 0, bytes.length)) != -1) {
                    baos.write(bytes, 0, nRead);
                }
                data = baos.toByteArray();
                filename = part.getSubmittedFileName();
            }
            return this;
        }
    }
}
