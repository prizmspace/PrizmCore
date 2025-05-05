/******************************************************************************
 * Copyright © 2013-2016 The prizm Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * prizm software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * @depends {nrs.js}
 */
var NRS = (function(NRS, $, undefined) {
    // If you add new mandatory attributes, please make sure to add them to
    // NRS.loadTransactionTypeConstants as well (below)
    NRS.transactionTypes = {
        0: {
            'title': "Payment",
            'i18nKeyTitle': 'payment',
            'iconHTML': "<i class='fa fa-money'></i>",
            'subTypes': {
                0: {
                    'title': "Ordinary Payment",
                    'i18nKeyTitle': 'ordinary_payment',
                    'iconHTML': "",
                    'receiverPage': 'transactions'
                }
            }
        },
        1: {
            'title': "Messaging",
            'i18nKeyTitle': 'messages',
            'iconHTML': "<i class='fa fa-envelope-o'></i>",
            'subTypes': {
                0: {
                    'title': "Arbitrary Message",
                    'i18nKeyTitle': 'arbitrary_message',
                    'iconHTML': "",
                    'receiverPage': 'messages'
                },
                1: {
                    'title': "Alias Assignment",
                    'i18nKeyTitle': 'alias_assignment',
                    'iconHTML': "<i class='fa fa-bookmark'></i>"
                },
                2: {
                    'title': "Poll Creation",
                    'i18nKeyTitle': 'poll_creation',
                    'iconHTML': "<i class='fa fa-check-square-o'></i>"
                },
                3: {
                    'title': "Vote Casting",
                    'i18nKeyTitle': 'vote_casting',
                    'iconHTML': "<i class='fa fa-check'></i>"
                },

                4: {
                    'title': "Hub Announcement",
                    'i18nKeyTitle': 'hub_announcement',
                    'iconHTML': "<i class='ion-radio-waves'></i>"
                },
                5: {
                    'title': "Account Info",
                    'i18nKeyTitle': 'account_info',
                    'iconHTML': "<i class='fa fa-info'></i>"
                },
                6: {
                    'title': "Alias Sale/Transfer",
                    'i18nKeyTitle': 'alias_sale_transfer',
                    'iconHTML': "<i class='fa fa-tag'></i>",
                    'receiverPage': "aliases"
                },
                7: {
                    'title': "Alias Buy",
                    'i18nKeyTitle': 'alias_buy',
                    'iconHTML': "<i class='fa fa-money'></i>",
                    'receiverPage': "aliases"
                },
                8: {
                    'title': "Alias Deletion",
                    'i18nKeyTitle': 'alias_deletion',
                    'iconHTML': "<i class='fa fa-times'></i>"
                },
                9: {
                    'title': "Transaction Approval",
                    'i18nKeyTitle': 'transaction_approval',
                    'iconHTML': "<i class='fa fa-gavel'></i>",
                    'receiverPage': "transactions"
                },
                10: {
                    'title': "Account Property",
                    'i18nKeyTitle': 'account_property',
                    'iconHTML': "<i class='fa fa-gavel'></i>",
                    'receiverPage': "transactions"
                },
                11: {
                    'title': "AccountPropertyDelete",
                    'i18nKeyTitle': 'account_property_delete',
                    'iconHTML': "<i class='fa fa-question'></i>",
                    'receiverPage': "transactions"
                }
            }
        },
	2: {
            'title': "Account Control",
            'i18nKeyTitle': 'account_control',
            'iconHTML': '<i class="ion-locked"></i>',
            'subTypes': {
                0: {
                    'title': "Balance Leasing",
                    'i18nKeyTitle': 'balance_leasing',
                    'iconHTML': '<i class="fa fa-arrow-circle-o-right"></i>',
                    'receiverPage': "transactions"
                },
                1: {
                    'title': "Mandatory Approval",
                    'i18nKeyTitle': 'phasing_only',
                    'iconHTML': '<i class="fa fa-gavel"></i>',
                    'receiverPage': "transactions"
                }
            }
        }
    };

    NRS.subtype = {};
    NRS.loadTransactionTypeConstants = function(response) {
        if (response.genesisAccountId) {
            $.each(response.transactionTypes, function(typeIndex, type) {
                if (!(typeIndex in NRS.transactionTypes)) {
                    NRS.transactionTypes[typeIndex] = {
                        'title': "Unknown",
                        'i18nKeyTitle': 'unknown',
                        'iconHTML': '<i class="fa fa-question-circle"></i>',
                        'subTypes': {}
                    }
                }
                $.each(type.subtypes, function(subTypeIndex, subType) {
                    if (!(subTypeIndex in NRS.transactionTypes[typeIndex]["subTypes"])) {
                        NRS.transactionTypes[typeIndex]["subTypes"][subTypeIndex] = {
                            'title': "Unknown",
                            'i18nKeyTitle': 'unknown',
                            'iconHTML': '<i class="fa fa-question-circle"></i>'
                        }
                    }
                    NRS.transactionTypes[typeIndex]["subTypes"][subTypeIndex]["serverConstants"] = subType;
                });
            });
            NRS.subtype = response.transactionSubTypes;
        }
    };

    NRS.isOfType = function(transaction, type_str) {
        if (!NRS.subtype[type_str]) {
            $.growl($.t("unsupported_transaction_type"));
            return;
        }
        return transaction.type == NRS.subtype[type_str].type && transaction.subtype == NRS.subtype[type_str].subtype;
    };

    return NRS;
}(NRS || {}, jQuery));
