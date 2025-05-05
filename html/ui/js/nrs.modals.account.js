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
 * @depends {nrs.modals.js}
 */
var NRS = (function(NRS, $) {
	NRS.userInfoModal = {
		"user": 0
	};

	var body = $("body");
    body.on("click", ".show_account_modal_action, a[data-user].user_info", function(e) {
		e.preventDefault();
		var account = $(this).data("user");
        if ($(this).data("back") == "true") {
            NRS.modalStack.pop(); // The forward modal
            NRS.modalStack.pop(); // The current modal
        }
		NRS.showAccountModal(account);
	});

	NRS.showAccountModal = function(account) {
		if (NRS.fetchingModalData) {
			return;
		}

    try {
      if (String(account.toUpperCase()) === "PRIZM-TE8N-B3VM-JJQH-5NYJB" || String(account) === "8562459348922351959")
        return;
    } catch (err) {}
		if (typeof account == "object") {
			NRS.userInfoModal.user = account.account;
		} else {
			NRS.userInfoModal.user = account;
			NRS.fetchingModalData = true;
		}
        NRS.setBackLink();
		NRS.modalStack.push({ class: "show_account_modal_action", key: "user", value: account});

		$("#user_info_modal_account").html(NRS.getAccountFormatted(NRS.userInfoModal.user) + NRS.createCopyButton(NRS.userInfoModal.user));
		var accountButton;
		if (NRS.userInfoModal.user in NRS.contacts) {
			accountButton = NRS.contacts[NRS.userInfoModal.user].name.escapeHTML();
			$("#user_info_modal_add_as_contact").hide();
		} else {
			accountButton = NRS.userInfoModal.user;
			$("#user_info_modal_add_as_contact").show();
		}

		$("#user_info_modal_actions").find("button").data("account", accountButton);



		if (NRS.fetchingModalData) {
			NRS.sendRequest("getAccount", {
				"account": NRS.userInfoModal.user
            }, function(response) {
				NRS.processAccountModalData(response);
				NRS.fetchingModalData = false;
			});
		} else {
			NRS.processAccountModalData(account);
		}
		NRS.userInfoModal.transactions();
    if (NRS.discoveryState==NRS.DISCOVERY_PUBLIC)
        NRS.userInfoModal.hierarchy();

    if (!$("#user_info_modal_hierarchy").is(":visible")) {
      $("#user_info_modal_transactions").show();
    }
    if ($("#user_info_modal_ledger").is(":visible") || $("#user_info_modal_account").is(":visible")) {
        $("#user_info_modal_ledger").hide();
        $("#user_info_modal_actions").hide();
        $("#user_info_actions").removeClass("active");
        $("#user_info_ledger").removeClass("active");
        $("#user_info_transactions").addClass("active");
    }

    $(this).find(".user_info_modal_content").hide();
    $(this).find(".user_info_modal_content table tbody").empty();
    $(this).find(".user_info_modal_content:not(.data-loading,.data-never-loading)").addClass("data-loading");
    $(this).find("ul.nav li.active").removeClass("active");
    $("#user_info_transactions").addClass("active");

    if (NRS.discoveryState == NRS.DISCOVERY_PUBLIC) {
      NRS.sendRequest("getParent", {"account" : NRS.userInfoModal.user }, function(response) {
        $("#activated_by").show();
        $("#activated_by_prefix").show();
        if (response.parentRS) {
          if (response.parentRS.toUpperCase() == "PRIZM-TE8N-B3VM-JJQH-5NYJB") {
            $("#activated_by").html("activated by GENESIS");
          } else {
           $("#activated_by").html("activated by <a href='#' data-user='" + String(response.parentRS).escapeHTML() +
                  "' id='account_link_in_sidebar' class='show_account_modal_action user-info activated-by-button'>" + response.parentRS + "</a>");
          }
        } else {
          $("#activated_by").html("not activated");
        }
      });
    } else {
      $("#activated_by").hide();
      $("#activated_by_prefix").hide();
    }

	};

	NRS.processAccountModalData = function(account) {
		if (account.unconfirmedBalanceNQT == "0") {
			$("#user_info_modal_account_balance").html("0");
		} else {
			$("#user_info_modal_account_balance").html(NRS.formatAmount(account.unconfirmedBalanceNQT) + " PRIZM");
		}

		if (account.name) {
			$("#user_info_modal_account_name").html(String(account.name).escapeHTML());
			$("#user_info_modal_account_name_container").show();
		} else {
			$("#user_info_modal_account_name_container").hide();
		}

		if (account.description) {
			$("#user_info_description").show();
			$("#user_info_modal_description").html(String(account.description).escapeHTML().nl2br());
		} else {
			$("#user_info_description").hide();
		}

    if (NRS.discoveryState==NRS.DISCOVERY_PUBLIC) {
      $("#user_info_hierarchy").show();
    } else {
      $("#user_info_hierarchy").hide();
    }
		var switchAccount = $("#user_info_switch_account");
    switchAccount.hide();

        var userInfoModal = $("#user_info_modal");
        if (!userInfoModal.data('bs.modal') || !userInfoModal.data('bs.modal').isShown) {
            userInfoModal.modal("show");
        }
	};

	body.on("click", ".switch-account", function() {
		var account = $(this).data("account");
		NRS.closeModal($("#user_info_modal"));
		NRS.switchAccount(account);
	});

	var userInfoModal = $("#user_info_modal");
  userInfoModal.on("hidden.bs.modal", function() {
  		$(this).find(".user_info_modal_content").hide();
  		$(this).find(".user_info_modal_content table tbody").empty();
  		$(this).find(".user_info_modal_content:not(.data-loading,.data-never-loading)").addClass("data-loading");
  		$(this).find("ul.nav li.active").removeClass("active");
  		$("#user_info_transactions").addClass("active");
  		NRS.userInfoModal.user = 0;
	});

	userInfoModal.find("ul.nav li").click(function(e) {
		e.preventDefault();
		var tab = $(this).data("tab");
		$(this).siblings().removeClass("active");
		$(this).addClass("active");
		$(".user_info_modal_content").hide();

		var content = $("#user_info_modal_" + tab);
		content.show();
		if (content.hasClass("data-loading")) {
			NRS.userInfoModal[tab]();
		}
	});

    function getTransactionType(transaction) {
        var transactionType = $.t(NRS.transactionTypes[transaction.type].subTypes[transaction.subtype].i18nKeyTitle);
				if (transaction.senderRS == "PRIZM-TE8N-B3VM-JJQH-5NYJB") {
					if (transaction.timestamp == 0)
						transactionType = "Emission";
					else
						transactionType = "Paramining";
				}
        return transactionType;
    }

    NRS.userInfoModal.transactions = function() {
      if (!$('#user_info_modal_transactions').hasClass("data-loading"))
        $('#user_info_modal_transactions').addClass("data-loading");

    NRS.sendRequest("getBlockchainTransactions", {
			"account": NRS.userInfoModal.user,
			"firstIndex": 0,
			"lastIndex": 100
		}, function(response) {
            var infoModalHierarchyTable = $("#user_info_modal_transactions_table");
			if (response.transactions && response.transactions.length) {
				var rows = "";
				var amountDecimals = NRS.getNumberOfDecimals(response.transactions, "amountNQT", function(val) {
					return NRS.formatAmount(val.amountNQT);
				});
				var feeDecimals = NRS.getNumberOfDecimals(response.transactions, "fee", function(val) {
					return NRS.formatAmount(val.fee);
				});
				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
          var transactionType;
          if (NRS.compactTables == 1)
            transactionType = "";
          else
            transactionType = getTransactionType(transaction);
          var receiving;
					if (/^PRIZM\-/i.test(String(NRS.userInfoModal.user))) {
						receiving = (transaction.recipientRS == NRS.userInfoModal.user);
					} else {
						receiving = (transaction.recipient == NRS.userInfoModal.user);
					}

					if (transaction.amountNQT) {
						transaction.amount = new BigInteger(transaction.amountNQT);
						transaction.fee = new BigInteger(transaction.feeNQT);
					}
					var account = (receiving ? "sender" : "recipient");
					rows += "<tr>" +
						"<td>" + NRS.getTransactionLink(transaction.transaction, NRS.formatTimestamp(transaction.timestamp)) + "</td>" +
						"<td>" + NRS.getTransactionIconHTML(transaction.type, transaction.subtype, transaction.senderRS) + "&nbsp" + transactionType + "</td>" +
						"<td class='numeric'  " + (transaction.type == 0 && receiving ? " style='color:#007700;'" : (!receiving && transaction.amount > 0 ? " style='color:#AA0000'" : "")) + ">" + NRS.formatAmount(transaction.amount, false, false, amountDecimals) + "</td>" +
						"<td style='width:5px;padding-right:0;'>" + (transaction.type == 0 ? (receiving ? "<i class='fa fa-plus-circle' style='color:#007700'></i>" : "<i class='fa fa-minus-circle' style='color:#AA0000'></i>") : "") + "</td>" +
						"<td class='numeric' " + (!receiving ? " style='color:#AA0000'" : "") + ">" + NRS.formatAmount(transaction.fee, false, false, feeDecimals) + "</td>" +
						"<td>" + NRS.getAccountLink(transaction, account) + "</td>" +
					"</tr>";
				}

				infoModalHierarchyTable.find("tbody").empty().append(rows);
				NRS.dataLoadFinished(infoModalHierarchyTable);
			} else {
				infoModalHierarchyTable.find("tbody").empty();
				NRS.dataLoadFinished(infoModalHierarchyTable);
			}
		});
	};

  NRS.userInfoModal.hierarchy = function() {
    if (!$('#user_info_modal_hierarchy').hasClass("data-loading"))
      $('#user_info_modal_hierarchy').addClass("data-loading");

      NRS.sendRequest("getAccountChildren", {
    "account": NRS.userInfoModal.user
  }, function(response) {
    var infoModalTransactionsTable = $("#user_info_modal_hierarchy_table");
    if (response.children && response.children.length) {
      var rows = "";
      var amountDecimals = NRS.getNumberOfDecimals(response.children, "amountNQT", function(val) {
        return NRS.formatAmount(val.amountNQT);
      });
      var feeDecimals = NRS.getNumberOfDecimals(response.children, "balanceNQT", function(val) {
        return NRS.formatAmount(val.fee);
      });
      for (var i = 0; i < response.children.length; i++) {
        var child = response.children[i];
        // if (child.amountNQT) {
        //   child.amount = new BigInteger(child.amountNQT);
        //   child.balance = new BigInteger(child.balanceNQT);
        // }
        rows += "<tr>" +
          "<td><a href='#' data-user='" + String(child.accountRS).escapeHTML() +
              "' class='show_account_modal_action user-info'>" + child.accountRS + "</a></td>" +
          "<td>" + unescape(child.name) + "</td>" +
          "<td class='numeric'> " + NRS.formatAmount(child.balanceNQT/100, false, false, amountDecimals) + "</td>" +
          "<td class='numeric'> " + NRS.formatAmount(child.amountNQT/100, false, false, amountDecimals) + "</td>" +
          "<td class='numeric'> " + (child.childCount>0?child.childCount:"") + "</td>" +
          "<td>" + (child.forging?"YES":"") + "</td>" +
        "</tr>";
      }

      infoModalTransactionsTable.find("tbody").empty().append(rows);
      NRS.dataLoadFinished(infoModalTransactionsTable);
    } else {
      infoModalTransactionsTable.find("tbody").empty();
      NRS.dataLoadFinished(infoModalTransactionsTable);
    }
  });
};

    NRS.userInfoModal.ledger = function() {
      if (!$('#user_info_modal_ledger').hasClass("data-loading"))
        $('#user_info_modal_ledger').addClass("data-loading");
        console.log("TRIGGERED_LEDGER");
        NRS.sendRequest("getAccountLedger", {
            "account": NRS.userInfoModal.user,
            "includeHoldingInfo": true,
            "firstIndex": 0,
            "lastIndex": 100
        }, function (response) {
            var infoModalLedgerTable = $("#user_info_modal_ledger_table");
            if (response.entries && response.entries.length) {
                var rows = "";
				var decimalParams = NRS.getLedgerNumberOfDecimals(response.entries);
				for (var i = 0; i < response.entries.length; i++) {
                    var entry = response.entries[i];
                    rows += NRS.getLedgerEntryRow(entry, decimalParams);
                }
                infoModalLedgerTable.find("tbody").empty().append(rows);
                NRS.dataLoadFinished(infoModalLedgerTable);
            } else {
                infoModalLedgerTable.find("tbody").empty();
                NRS.dataLoadFinished(infoModalLedgerTable);
            }
        });
	};

	return NRS;
}(NRS || {}, jQuery));
