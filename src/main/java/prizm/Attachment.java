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

package prizm;

import prizm.crypto.Crypto;
import prizm.crypto.EncryptedData;
import prizm.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface Attachment extends Appendix {

    TransactionType getTransactionType();

    abstract class AbstractAttachment extends Appendix.AbstractAppendix implements Attachment {

        private AbstractAttachment(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        private AbstractAttachment(JSONObject attachmentData) {
            super(attachmentData);
        }

        private AbstractAttachment(int version) {
            super(version);
        }

        private AbstractAttachment() {}

        @Override
        final String getAppendixName() {
            return getTransactionType().getName();
        }

        @Override
        final void validate(Transaction transaction) throws PrizmException.ValidationException {
            getTransactionType().validateAttachment(transaction);
        }

        @Override
        final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            getTransactionType().apply((TransactionImpl) transaction, senderAccount, recipientAccount);
        }

        @Override
        public final Fee getBaselineFee(Transaction transaction) {
            return getTransactionType().getBaselineFee(transaction);
        }

        @Override
        public final Fee getNextFee(Transaction transaction) {
            return getTransactionType().getNextFee(transaction);
        }

        @Override
        public final int getBaselineFeeHeight() {
            return getTransactionType().getBaselineFeeHeight();
        }

        @Override
        public final int getNextFeeHeight() {
            return getTransactionType().getNextFeeHeight();
        }
    }

    abstract class EmptyAttachment extends AbstractAttachment {

        private EmptyAttachment() {
            super(0);
        }

        @Override
        final int getMySize() {
            return 0;
        }

        @Override
        final void putMyBytes(ByteBuffer buffer) {
        }

        @Override
        final void putMyJSON(JSONObject json) {
        }

        @Override
        final boolean verifyVersion(byte transactionVersion) {
            return getVersion() == 0;
        }

    }

    EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Payment.ORDINARY;
        }

    };

    // the message payload is in the Appendix
    EmptyAttachment ARBITRARY_MESSAGE = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ARBITRARY_MESSAGE;
        }

    };

    final class MessagingAliasAssignment extends AbstractAttachment {

        private final String aliasName;
        private final String aliasURI;

        MessagingAliasAssignment(ByteBuffer buffer, byte transactionVersion) throws PrizmException.NotValidException {
            super(buffer, transactionVersion);
            aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH).trim();
            aliasURI = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ALIAS_URI_LENGTH).trim();
        }

        MessagingAliasAssignment(JSONObject attachmentData) {
            super(attachmentData);
            aliasName = Convert.nullToEmpty((String) attachmentData.get("alias")).trim();
            aliasURI = Convert.nullToEmpty((String) attachmentData.get("uri")).trim();
        }

        public MessagingAliasAssignment(String aliasName, String aliasURI) {
            this.aliasName = aliasName.trim();
            this.aliasURI = aliasURI.trim();
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length + 2 + Convert.toBytes(aliasURI).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] alias = Convert.toBytes(this.aliasName);
            byte[] uri = Convert.toBytes(this.aliasURI);
            buffer.put((byte)alias.length);
            buffer.put(alias);
            buffer.putShort((short) uri.length);
            buffer.put(uri);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
            attachment.put("uri", aliasURI);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_ASSIGNMENT;
        }

        public String getAliasName() {
            return aliasName;
        }

        public String getAliasURI() {
            return aliasURI;
        }
    }

    final class MessagingAliasDelete extends AbstractAttachment {

        private final String aliasName;

        MessagingAliasDelete(final ByteBuffer buffer, final byte transactionVersion) throws PrizmException.NotValidException {
            super(buffer, transactionVersion);
            this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
        }

        MessagingAliasDelete(final JSONObject attachmentData) {
            super(attachmentData);
            this.aliasName = Convert.nullToEmpty((String) attachmentData.get("alias"));
        }

        public MessagingAliasDelete(final String aliasName) {
            this.aliasName = aliasName;
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_DELETE;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length;
        }

        @Override
        void putMyBytes(final ByteBuffer buffer) {
            byte[] aliasBytes = Convert.toBytes(aliasName);
            buffer.put((byte)aliasBytes.length);
            buffer.put(aliasBytes);
        }

        @Override
        void putMyJSON(final JSONObject attachment) {
            attachment.put("alias", aliasName);
        }

        public String getAliasName(){
            return aliasName;
        }
    }

    final class MessagingAccountInfo extends AbstractAttachment {

        private final String name;
        private final String description;

        MessagingAccountInfo(ByteBuffer buffer, byte transactionVersion) throws PrizmException.NotValidException {
            super(buffer, transactionVersion);
            this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
        }

        MessagingAccountInfo(JSONObject attachmentData) {
            super(attachmentData);
            this.name = Convert.nullToEmpty((String) attachmentData.get("name"));
            this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
        }

        public MessagingAccountInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.name);
            byte[] description = Convert.toBytes(this.description);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ACCOUNT_INFO;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

    }
}
