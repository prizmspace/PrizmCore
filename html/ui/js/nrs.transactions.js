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

	NRS.lastTransactions = "";
	NRS.unconfirmedTransactions = [];
	NRS.unconfirmedTransactionIds = "";
	NRS.unconfirmedTransactionsChange = true;
  NRS.isHierarchyTableMinified = false;
  var colors = ["#dff4d5", "#ead5f4", "#f4efd5", "#f4d5df"];
  NRS.nextColor = function(sublevel) {
    return "color"+(sublevel%4 + 1);
    // return colors[sublevel%colors.length];
  }

	NRS.handleIncomingTransactions = function(transactions, confirmedTransactionIds) {
		var oldBlock = (confirmedTransactionIds === false); //we pass false instead of an [] in case there is no new block..

		if (typeof confirmedTransactionIds != "object") {
			confirmedTransactionIds = [];
		}

		if (confirmedTransactionIds.length) {
			NRS.lastTransactions = confirmedTransactionIds.toString();
		}

		if (confirmedTransactionIds.length || NRS.unconfirmedTransactionsChange) {
			transactions.sort(NRS.sortArray);
		}
		//Bug with popovers staying permanent when being open
		$('div.popover').hide();
		$('.td_transaction_phasing div.show_popover').popover('hide');

		//always refresh peers and unconfirmed transactions..
		if (NRS.currentPage == "peers") {
			NRS.incoming.peers();
		} else if (NRS.currentPage == "transactions"
            && $('#transactions_type_navi').find('li.active a').attr('data-transaction-type') == "unconfirmed") {
			NRS.incoming.transactions();
		} else {
			if (NRS.currentPage != 'messages' && (!oldBlock || NRS.unconfirmedTransactionsChange)) {
				if (NRS.incoming[NRS.currentPage]) {
					NRS.incoming[NRS.currentPage](transactions);
				}
			}
		}
		if (!oldBlock || NRS.unconfirmedTransactionsChange) {
			// always call incoming for messages to enable message notifications
			NRS.incoming['messages'](transactions);
			NRS.updateNotifications();
			NRS.setPhasingNotifications();
		}
	};

	NRS.getUnconfirmedTransactions = function(callback) {
		NRS.sendRequest("getUnconfirmedTransactions", {
			"account": NRS.account
		}, function(response) {
			if (response.unconfirmedTransactions && response.unconfirmedTransactions.length) {
				var unconfirmedTransactions = [];
				var unconfirmedTransactionIds = [];

				response.unconfirmedTransactions.sort(function(x, y) {
					if (x.timestamp < y.timestamp) {
						return 1;
					} else if (x.timestamp > y.timestamp) {
						return -1;
					} else {
						return 0;
					}
				});

				for (var i = 0; i < response.unconfirmedTransactions.length; i++) {
					var unconfirmedTransaction = response.unconfirmedTransactions[i];
					unconfirmedTransaction.confirmed = false;
					unconfirmedTransaction.unconfirmed = true;
					unconfirmedTransaction.confirmations = "/";

					if (unconfirmedTransaction.attachment) {
						for (var key in unconfirmedTransaction.attachment) {
							if (!unconfirmedTransaction.attachment.hasOwnProperty(key)) {
								continue;
							}
							if (!unconfirmedTransaction.hasOwnProperty(key)) {
								unconfirmedTransaction[key] = unconfirmedTransaction.attachment[key];
							}
						}
					}
					unconfirmedTransactions.push(unconfirmedTransaction);
					unconfirmedTransactionIds.push(unconfirmedTransaction.transaction);
				}
				NRS.unconfirmedTransactions = unconfirmedTransactions;
				var unconfirmedTransactionIdString = unconfirmedTransactionIds.toString();
				if (unconfirmedTransactionIdString != NRS.unconfirmedTransactionIds) {
					NRS.unconfirmedTransactionsChange = true;
					NRS.setUnconfirmedNotifications();
					NRS.unconfirmedTransactionIds = unconfirmedTransactionIdString;
				} else {
					NRS.unconfirmedTransactionsChange = false;
				}

				if (callback) {
					callback(unconfirmedTransactions);
				}
			} else {
				NRS.unconfirmedTransactions = [];
				if (NRS.unconfirmedTransactionIds) {
					NRS.unconfirmedTransactionsChange = true;
					NRS.setUnconfirmedNotifications();
				} else {
					NRS.unconfirmedTransactionsChange = false;
				}

				NRS.unconfirmedTransactionIds = "";
				if (callback) {
					callback([]);
				}
			}
		});
	};

	NRS.getInitialTransactions = function() {
		NRS.sendRequest("getBlockchainTransactions", {
			"account": NRS.account,
			"firstIndex": 0,
			"lastIndex": 9
		}, function(response) {
			if (response.transactions && response.transactions.length) {
				var transactions = [];
				var transactionIds = [];

				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
					transaction.confirmed = true;
					transactions.push(transaction);
					transactionIds.push(transaction.transaction);
				}
				NRS.getUnconfirmedTransactions(function() {
					NRS.loadPage('dashboard');
				});
			} else {
				NRS.getUnconfirmedTransactions(function() {
					NRS.loadPage('dashboard');
				});
			}
		});
	};

	NRS.getNewTransactions = function() {
		//check if there is a new transaction..
		if (!NRS.blocks[0]) {
			return;
		}
        NRS.sendRequest("getBlockchainTransactions", {
			"account": NRS.account,
			"timestamp": NRS.blocks[0].timestamp + 1,
			"firstIndex": 0,
			"lastIndex": 0
		}, function(response) {
			//if there is, get latest 10 transactions
			if (response.transactions && response.transactions.length) {
				NRS.sendRequest("getBlockchainTransactions", {
					"account": NRS.account,
					"firstIndex": 0,
					"lastIndex": 9
				}, function(response) {
					if (response.transactions && response.transactions.length) {
						var transactionIds = [];

						$.each(response.transactions, function(key, transaction) {
							transactionIds.push(transaction.transaction);
							response.transactions[key].confirmed = true;
						});

						NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							NRS.handleIncomingTransactions(response.transactions.concat(unconfirmedTransactions), transactionIds);
						});
					} else {
						NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							NRS.handleIncomingTransactions(unconfirmedTransactions);
						});
					}
				});
			} else {
				NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
					NRS.handleIncomingTransactions(unconfirmedTransactions);
				});
			}
		});
	};

	NRS.addUnconfirmedTransaction = function(transactionId, callback) {
		NRS.sendRequest("getTransaction", {
			"transaction": transactionId
		}, function(response) {
			if (!response.errorCode) {
				response.transaction = transactionId;
				response.confirmations = "/";
				response.confirmed = false;
				response.unconfirmed = true;

				if (response.attachment) {
					for (var key in response.attachment) {
                        if (!response.attachment.hasOwnProperty(key)) {
                            continue;
                        }
						if (!response.hasOwnProperty(key)) {
							response[key] = response.attachment[key];
						}
					}
				}
				var alreadyProcessed = false;
				try {
					var regex = new RegExp("(^|,)" + transactionId + "(,|$)");
					if (regex.exec(NRS.lastTransactions)) {
						alreadyProcessed = true;
					} else {
						$.each(NRS.unconfirmedTransactions, function(key, unconfirmedTransaction) {
							if (unconfirmedTransaction.transaction == transactionId) {
								alreadyProcessed = true;
								return false;
							}
						});
					}
				} catch (e) {
                    NRS.logConsole(e.message);
                }

				if (!alreadyProcessed) {
					NRS.unconfirmedTransactions.unshift(response);
				}
				if (callback) {
					callback(alreadyProcessed);
				}
				if (NRS.currentPage == 'transactions' || NRS.currentPage == 'dashboard') {
					$('div.popover').hide();
					$('.td_transaction_phasing div.show_popover').popover('hide');
					NRS.incoming[NRS.currentPage]();
				}

				NRS.getAccountInfo();
			} else if (callback) {
				callback(false);
			}
		});
	};

	NRS.sortArray = function(a, b) {
		return b.timestamp - a.timestamp;
	};

	NRS.getTransactionIconHTML = function(type, subtype, senderRS) {
		var iconHTML = NRS.transactionTypes[type]['iconHTML'] + " " + NRS.transactionTypes[type]['subTypes'][subtype]['iconHTML'];
		var tooltip = $.t(NRS.transactionTypes[type].subTypes[subtype].i18nKeyTitle);
    if (senderRS != null && String(senderRS) == "PRIZM-TE8N-B3VM-JJQH-5NYJB") {
      iconHTML = "<img src='img/icon-pickaxe.svg' style='width:12px;height:12px;'/>";
      tooltip = $.t("paramining");
    }
		return '<span title="' + tooltip + '" class="label label-primary" style="font-size:12px;">' + iconHTML + '</span>';
	};

	NRS.addPhasedTransactionHTML = function(t) {
		var $tr = $('.tr_transaction_' + t.transaction + ':visible');
		var $tdPhasing = $tr.find('.td_transaction_phasing');
		var $approveBtn = $tr.find('.td_transaction_actions .approve_transaction_btn');

		if (t.attachment && t.attachment["version.Phasing"] && t.attachment.phasingVotingModel != undefined) {
			NRS.sendRequest("getPhasingPoll", {
				"transaction": t.transaction,
				"countVotes": true
			}, function(responsePoll) {
				if (responsePoll.transaction) {
					NRS.sendRequest("getPhasingPollVote", {
						"transaction": t.transaction,
						"account": NRS.accountRS
					}, function(responseVote) {
						var attachment = t.attachment;
						var vm = attachment.phasingVotingModel;
						var minBalance = parseFloat(attachment.phasingMinBalance);
						var mbModel = attachment.phasingMinBalanceModel;

						if ($approveBtn) {
							var disabled = false;
							var unconfirmedTransactions = NRS.unconfirmedTransactions;
							if (unconfirmedTransactions) {
								for (var i = 0; i < unconfirmedTransactions.length; i++) {
									var ut = unconfirmedTransactions[i];
									if (ut.attachment && ut.attachment["version.PhasingVoteCasting"] && ut.attachment.transactionFullHashes && ut.attachment.transactionFullHashes.length > 0) {
										if (ut.attachment.transactionFullHashes[0] == t.fullHash) {
											disabled = true;
											$approveBtn.attr('disabled', true);
										}
									}
								}
							}
							if (!disabled) {
								if (responseVote.transaction) {
									$approveBtn.attr('disabled', true);
								} else {
									$approveBtn.attr('disabled', false);
								}
							}
						}

						if (!responsePoll.result) {
							responsePoll.result = 0;
						}

						var state = "";
						var color = "";
						var icon = "";
						var minBalanceFormatted = "";
                        var finished = attachment.phasingFinishHeight <= NRS.lastBlockHeight;
						var finishHeightFormatted = String(attachment.phasingFinishHeight);
						var percentageFormatted = attachment.phasingQuorum > 0 ? NRS.calculatePercentage(responsePoll.result, attachment.phasingQuorum, 0) + "%" : "";
						var percentageProgressBar = attachment.phasingQuorum > 0 ? Math.round(responsePoll.result * 100 / attachment.phasingQuorum) : 0;
						var progressBarWidth = Math.round(percentageProgressBar / 2);
                        var approvedFormatted;
						if (responsePoll.approved || attachment.phasingQuorum == 0) {
							approvedFormatted = "Yes";
						} else {
							approvedFormatted = "No";
						}

						if (finished) {
							if (responsePoll.approved) {
								state = "success";
								color = "#00a65a";
							} else {
								state = "danger";
								color = "#f56954";
							}
						} else {
							state = "warning";
							color = "#f39c12";
						}

						var $popoverTable = $("<table class='table table-striped'></table>");
						var $popoverTypeTR = $("<tr><td></td><td></td></tr>");
						var $popoverVotesTR = $("<tr><td>" + $.t('votes', 'Votes') + ":</td><td></td></tr>");
						var $popoverPercentageTR = $("<tr><td>" + $.t('percentage', 'Percentage') + ":</td><td></td></tr>");
						var $popoverFinishTR = $("<tr><td>" + $.t('finish_height', 'Finish Height') + ":</td><td></td></tr>");
						var $popoverApprovedTR = $("<tr><td>" + $.t('approved', 'Approved') + ":</td><td></td></tr>");

						$popoverTypeTR.appendTo($popoverTable);
						$popoverVotesTR.appendTo($popoverTable);
						$popoverPercentageTR.appendTo($popoverTable);
						$popoverFinishTR.appendTo($popoverTable);
						$popoverApprovedTR.appendTo($popoverTable);

						$popoverPercentageTR.find("td:last").html(percentageFormatted);
						$popoverFinishTR.find("td:last").html(finishHeightFormatted);
						$popoverApprovedTR.find("td:last").html(approvedFormatted);

						var template = '<div class="popover" style="min-width:260px;"><div class="arrow"></div><div class="popover-inner">';
						template += '<h3 class="popover-title"></h3><div class="popover-content"><p></p></div></div></div>';

						var popoverConfig = {
							"html": true,
							"trigger": "hover",
							"placement": "top",
							"template": template
						};

						if (vm == -1) {
							icon = '<i class="fa ion-load-a"></i>';
						}
						if (vm == 0) {
							icon = '<i class="fa fa-group"></i>';
						}
						if (vm == 1) {
							icon = '<i class="fa fa-money"></i>';
						}
						if (vm == 4) {
							icon = '<i class="fa fa-signal"></i>';
						}
						if (vm == 3) {
							icon = '<i class="fa fa-bank"></i>';
						}
						if (vm == 2) {
							icon = '<i class="fa fa-thumbs-up"></i>';
						}
						if (vm == 5) {
							icon = '<i class="fa fa-question"></i>';
						}
						var phasingDiv = "";
						phasingDiv += '<div class="show_popover" style="display:inline-block;min-width:94px;text-align:left;border:1px solid #e2e2e2;background-color:#fff;padding:3px;" ';
	 				 	phasingDiv += 'data-toggle="popover" data-container="body">';
						phasingDiv += "<div class='label label-" + state + "' style='display:inline-block;margin-right:5px;'>" + icon + "</div>";

						if (vm == -1) {
							phasingDiv += '<span style="color:' + color + '">' + $.t("none") + '</span>';
						} else if (vm == 0) {
							phasingDiv += '<span style="color:' + color + '">' + String(responsePoll.result) + '</span> / <span>' + String(attachment.phasingQuorum) + '</span>';
						} else {
							phasingDiv += '<div class="progress" style="display:inline-block;height:10px;width: 50px;">';
	    					phasingDiv += '<div class="progress-bar progress-bar-' + state + '" role="progressbar" aria-valuenow="' + percentageProgressBar + '" ';
	    					phasingDiv += 'aria-valuemin="0" aria-valuemax="100" style="height:10px;width: ' + progressBarWidth + 'px;">';
	      					phasingDiv += '<span class="sr-only">' + percentageProgressBar + '% Complete</span>';
	    					phasingDiv += '</div>';
	  						phasingDiv += '</div> ';
	  					}
						phasingDiv += "</div>";
						var $phasingDiv = $(phasingDiv);
						popoverConfig["content"] = $popoverTable;
						$phasingDiv.popover(popoverConfig);
						$phasingDiv.appendTo($tdPhasing);
                        var votesFormatted;
						if (vm == 0) {
							$popoverTypeTR.find("td:first").html($.t('accounts', 'Accounts') + ":");
							$popoverTypeTR.find("td:last").html(String(attachment.phasingWhitelist ? attachment.phasingWhitelist.length : ""));
							votesFormatted = String(responsePoll.result) + " / " + String(attachment.phasingQuorum);
							$popoverVotesTR.find("td:last").html(votesFormatted);
						}
						if (vm == 1) {
							$popoverTypeTR.find("td:first").html($.t('accounts', 'Accounts') + ":");
							$popoverTypeTR.find("td:last").html(String(attachment.phasingWhitelist ? attachment.phasingWhitelist.length : ""));
							votesFormatted = NRS.convertToPRIZM(responsePoll.result) + " / " + NRS.convertToPRIZM(attachment.phasingQuorum) + " PRIZM";
							$popoverVotesTR.find("td:last").html(votesFormatted);
						}
						if (mbModel == 1) {
							if (minBalance > 0) {
								minBalanceFormatted = NRS.convertToPRIZM(minBalance) + " PRIZM";
								$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
							}
						}
						if (vm == 2 || mbModel == 2) {
							NRS.sendRequest("getAsset", {
								"asset": attachment.phasingHolding
							}, function(phResponse) {
								if (phResponse && phResponse.asset) {
									if (vm == 2) {
										$popoverTypeTR.find("td:first").html($.t('asset', 'Asset') + ":");
										$popoverTypeTR.find("td:last").html(String(phResponse.name));
										var votesFormatted = NRS.convertToQNTf(responsePoll.result, phResponse.decimals) + " / ";
										votesFormatted += NRS.convertToQNTf(attachment.phasingQuorum, phResponse.decimals) + " QNT";
										$popoverVotesTR.find("td:last").html(votesFormatted);
									}
									if (mbModel == 2) {
										if (minBalance > 0) {
											minBalanceFormatted = NRS.convertToQNTf(minBalance, phResponse.decimals) + " QNT (" + phResponse.name + ")";
											$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
										}
									}
								}
							}, false);
						}
						if (vm == 3 || mbModel == 3) {
							NRS.sendRequest("getCurrency", {
								"currency": attachment.phasingHolding
							}, function(phResponse) {
								if (phResponse && phResponse.currency) {
									if (vm == 3) {
										$popoverTypeTR.find("td:first").html($.t('currency', 'Currency') + ":");
										$popoverTypeTR.find("td:last").html(String(phResponse.code));
										var votesFormatted = NRS.convertToQNTf(responsePoll.result, phResponse.decimals) + " / ";
										votesFormatted += NRS.convertToQNTf(attachment.phasingQuorum, phResponse.decimals) + " Units";
										$popoverVotesTR.find("td:last").html(votesFormatted);
									}
									if (mbModel == 3) {
										if (minBalance > 0) {
											minBalanceFormatted = NRS.convertToQNTf(minBalance, phResponse.decimals) + " Units (" + phResponse.code + ")";
											$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
										}
									}
								}
							}, false);
						}
					});
				} else {
					$tdPhasing.html("&nbsp;");
				}
			}, false);
		} else {
			$tdPhasing.html("&nbsp;");
		}
	};

	NRS.addPhasingInfoToTransactionRows = function(transactions) {
		for (var i = 0; i < transactions.length; i++) {
			var transaction = transactions[i];
			NRS.addPhasedTransactionHTML(transaction);
		}
	};

  NRS.getTransactionRowHTML = function(t, actions, decimals) {
		var transactionType = $.t(NRS.transactionTypes[t.type]['subTypes'][t.subtype]['i18nKeyTitle']);

		if (t.type == 1 && t.subtype == 6 && t.attachment.priceNQT == "0") {
			if (t.sender == NRS.account && t.recipient == NRS.account) {
				transactionType = $.t("alias_sale_cancellation");
			} else {
				transactionType = $.t("alias_transfer");
			}
		}

		var amount = "";
		var sign = 0;
		var fee = new BigInteger(t.feeNQT);
		var feeColor = "";
		var receiving = t.recipient == NRS.account && !(t.sender == NRS.account);
		if (receiving) {
			if (t.amountNQT != "0") {
				amount = new BigInteger(t.amountNQT);
				sign = 1;
			}
			feeColor = "color:black;";
		} else {
			if (t.sender != t.recipient) {
				if (t.amountNQT != "0") {
					amount = new BigInteger(t.amountNQT);
					amount = amount.negate();
					sign = -1;
				}
			} else {
				if (t.amountNQT != "0") {
					amount = new BigInteger(t.amountNQT); // send to myself
				}
			}
			feeColor = "color:red;";
		}
		var formattedAmount = "";
		if (amount != "") {
			formattedAmount = NRS.formatAmount(amount, false, false, decimals.amount);
		}
		var formattedFee = NRS.formatAmount(fee, false, false, decimals.fee);
		var amountColor = (sign == 1 ? "color:green;" : (sign == -1 ? "color:red;" : "color:black;"));
		var hasMessage = false;

		if (t.attachment) {
			if (t.attachment.encryptedMessage || t.attachment.message) {
				hasMessage = true;
			} else if (t.sender == NRS.account && t.attachment.encryptToSelfMessage) {
				hasMessage = true;
			}
		}
		var html = "";
		html += "<tr class='tr_transaction_" + t.transaction + "'>";
		html += "<td style='vertical-align:middle;'>";
  		html += "<a class='show_transaction_modal_action' href='#' data-timestamp='" + String(t.timestamp).escapeHTML() + "' ";
  		html += "data-transaction='" + String(t.transaction).escapeHTML() + "'>";
  		html += NRS.formatTimestamp(t.timestamp) + "</a>";
  		html += "</td>";
      if (NRS.compactTables != 1)
        		html += "<td style='vertical-align:middle;text-align:center;'>" + (hasMessage ? "&nbsp; <i class='fa fa-envelope-o'></i>&nbsp;" : "&nbsp;") + "</td>";
		html += '<td style="vertical-align:middle;">';
		html += NRS.getTransactionIconHTML(t.type, t.subtype, t.senderRS) + '&nbsp; ';
    if (NRS.compactTables != 1)
    		html += '<span style="font-size:11px;display:inline-block;margin-top:5px;">' + transactionType + '</span>';
		html += '</td>';
        html += "<td style='vertical-align:middle;text-align:right;" + amountColor + "'>" + formattedAmount + "</td>";
        html += "<td style='vertical-align:middle;text-align:right;" + feeColor + "'>" + formattedFee + "</td>";
		html += "<td style='vertical-align:middle;'>" + ((NRS.getAccountLink(t, "sender") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "sender")) + " ";
		html += "<i class='fa fa-arrow-circle-right' style='color:#777;'></i> " + ((NRS.getAccountLink(t, "recipient") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "recipient")) + "</td>";
		html += "<td style='vertical-align:middle;text-align:center;'>" + NRS.getBlockLink(t.height, null, true) + "</td>";
    if (NRS.compactTables != 1) {
  		html += "<td class='confirmations' style='vertical-align:middle;text-align:center;font-size:12px;'>";
  		html += "<span class='show_popover' data-content='" + (t.confirmed ? NRS.formatAmount(t.confirmations) + " " + $.t("confirmations") : $.t("unconfirmed_transaction")) + "' ";
  		html += "data-container='body' data-placement='left'>";
  		html += (!t.confirmed ? "-" : (t.confirmations > 1440 ? (NRS.formatAmount('144000') + "+") : NRS.formatAmount(t.confirmations))) + "</span></td>";
    }
		if (actions && actions.length != undefined) {
			html += '<td class="td_transaction_actions" style="vertical-align:middle;text-align:right;">';
			if (actions.indexOf('approve') > -1) {
                html += "<a class='btn btn-xs btn-default approve_transaction_btn' href='#' data-toggle='modal' data-target='#approve_transaction_modal' ";
				html += "data-transaction='" + String(t.transaction).escapeHTML() + "' data-fullhash='" + String(t.fullHash).escapeHTML() + "' ";
				html += "data-timestamp='" + t.timestamp + "' " + "data-votingmodel='" + t.attachment.phasingVotingModel + "' ";
				html += "data-fee='1' data-min-balance-formatted=''>" + $.t('approve') + "</a>";
			}
			html += "</td>";
		}
		html += "</tr>";
		return html;
	};

    NRS.getLedgerEntryRow = function(entry, decimalParams) {
        var linkClass;
        var dataToken;
        if (entry.isTransactionEvent) {
            linkClass = "show_transaction_modal_action";
            dataToken = "data-transaction='" + String(entry.event).escapeHTML() + "'";
        } else {
            linkClass = "show_block_modal_action";
            dataToken = "data-id='1' data-block='" + String(entry.event).escapeHTML()+ "'";
        }
        var change = entry.change;
        var balance = entry.balance;
        var balanceType = "prizm";
        var balanceEntity = "PRIZM";
        var holdingIcon = "";
        if (change < 0) {
            change = String(change).substring(1);
        }
        change = NRS.formatAmount(change, false, false, decimalParams.changeDecimals);
        balance = NRS.formatAmount(balance, false, false, decimalParams.balanceDecimals);
        var sign = "";
		var color = "";
        if (entry.change > 0) {
			color = "color:green;";
		} else if (entry.change < 0) {
			color = "color:red;";
			sign = "-";
        }
        var eventType = String(entry.eventType).escapeHTML();
        if (eventType.indexOf("ASSET") == 0 || eventType.indexOf("CURRENCY") == 0) {
            eventType = eventType.substring(eventType.indexOf("_") + 1);
        }
        eventType = $.t(eventType.toLowerCase());
        var html = "";
		html += "<tr>";
		html += "<td style='vertical-align:middle;'>";
  		html += "<a class='show_ledger_modal_action' href='#' data-entry='" + String(entry.ledgerId).escapeHTML() +"'";
        html += "data-change='" + (entry.change < 0 ? ("-" + change) : change) + "' data-balance='" + balance + "'>";
  		html += NRS.formatTimestamp(entry.timestamp) + "</a>";
  		html += "</td>";
		html += '<td style="vertical-align:middle;">';
        html += '<span style="font-size:11px;display:inline-block;margin-top:5px;">' + eventType + '</span>';
        html += "<a class='" + linkClass + "' href='#' data-timestamp='" + String(entry.timestamp).escapeHTML() + "' " + dataToken + ">";
        html += " <i class='fa fa-info'></i></a>";
		html += '</td>';
		if (balanceType == "prizm") {
            html += "<td style='vertical-align:middle;" + color + "' class='numeric'>" + sign + change + "</td>";
            html += "<td style='vertical-align:middle;' class='numeric'>" + balance + "</td>";
        } else {
            html += "<td></td>";
            html += "<td></td>";
            html += "<td>" + holdingIcon + balanceEntity + "</td>";
        }
		return html;
	};

	NRS.buildTransactionsTypeNavi = function() {
		var html = '';
		html += '<li role="presentation" class="active"><a href="#" data-transaction-type="" ';
		html += 'data-toggle="popover" data-placement="top" data-content="All" data-container="body" data-i18n="[data-content]all">';
		html += '<span data-i18n="all">All</span></a></li>';
        var typeNavi = $('#transactions_type_navi');
        typeNavi.append(html);

		$.each(NRS.transactionTypes, function(typeIndex, typeDict) {
			var titleString = $.t(typeDict.i18nKeyTitle);
			html = '<li role="presentation"><a href="#" data-transaction-type="' + typeIndex + '" ';
			html += 'data-toggle="popover" data-placement="top" data-content="' + titleString + '" data-container="body">';
			html += typeDict.iconHTML + '</a></li>';
if(typeIndex == 0 || typeIndex == 1  )	$('#transactions_type_navi').append(html);
		});

		html  = '<li role="presentation"><a href="#" data-transaction-type="unconfirmed" ';
		html += 'data-toggle="popover" data-placement="top" data-content="Unconfirmed (Account)" data-container="body" data-i18n="[data-content]unconfirmed_account">';
		html += '<i class="fa fa-gavel"></i>&nbsp; <span data-i18n="unconfirmed">Unconfirmed</span></a></li>';
		typeNavi.append(html);

		html  = '<li role="presentation"><a href="#" data-transaction-type="all_unconfirmed" ';
		html += 'data-toggle="popover" data-placement="top" data-content="Unconfirmed (Everyone)" data-container="body" data-i18n="[data-content]unconfirmed_everyone">';
		html += '<i class="fa fa-gavel"></i>&nbsp; <span data-i18n="all_unconfirmed">Unconfirmed (Everyone)</span></a></li>';
		typeNavi.append(html);

        typeNavi.find('a[data-toggle="popover"]').popover({
			"trigger": "hover"
		});
        typeNavi.find("[data-i18n]").i18n();
	};

	NRS.buildTransactionsSubTypeNavi = function() {
        var subtypeNavi = $('#transactions_sub_type_navi');
        subtypeNavi.empty();
		var html  = '<li role="presentation" class="active"><a href="#" data-transaction-sub-type="">';
		html += '<span>' + $.t("all_types") + '</span></a></li>';
		subtypeNavi.append(html);

		var typeIndex = $('#transactions_type_navi').find('li.active a').attr('data-transaction-type');
		if (typeIndex && typeIndex != "unconfirmed" && typeIndex != "all_unconfirmed" && typeIndex != "phasing") {
			var typeDict = NRS.transactionTypes[typeIndex];
			$.each(typeDict["subTypes"], function(subTypeIndex, subTypeDict) {
				var subTitleString = $.t(subTypeDict.i18nKeyTitle);
				html = '<li role="presentation"><a href="#" data-transaction-sub-type="' + subTypeIndex + '">';
				html += subTypeDict.iconHTML + ' ' + subTitleString + '</a></li>';
				$('#transactions_sub_type_navi').append(html);
			});
		}
	};

    NRS.displayUnconfirmedTransactions = function(account) {
        var params = {
            "firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
            "lastIndex": NRS.pageNumber * NRS.itemsPerPage
        };
        if (account != "") {
            params["account"] = account;
        }
        NRS.sendRequest("getUnconfirmedTransactions", params, function(response) {
			var rows = "";
			if (response.unconfirmedTransactions && response.unconfirmedTransactions.length) {
				var decimals = NRS.getTransactionsAmountDecimals(response.unconfirmedTransactions);
				for (var i = 0; i < response.unconfirmedTransactions.length; i++) {
                    rows += NRS.getTransactionRowHTML(response.unconfirmedTransactions[i], false, decimals);
				}
			}
			NRS.dataLoaded(rows);
		});
	};

	NRS.displayPhasedTransactions = function() {
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		};
		NRS.sendRequest("getAccountPhasedTransactions", params, function(response) {
			var rows = "";

			if (response.transactions && response.transactions.length) {
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var t = response.transactions[i];
					t.confirmed = true;
					rows += NRS.getTransactionRowHTML(t, false, decimals);
				}
				NRS.dataLoaded(rows);
				NRS.addPhasingInfoToTransactionRows(response.transactions);
			} else {
				NRS.dataLoaded(rows);
			}

		});
	};

    NRS.pages.dashboard = function() {
        var rows = "";
        var params = {
            "account": NRS.account,
            "firstIndex": 0,
            "lastIndex": 9
        };
        var unconfirmedTransactions = NRS.unconfirmedTransactions;
		var decimals = NRS.getTransactionsAmountDecimals(unconfirmedTransactions);
        if (unconfirmedTransactions) {
            for (var i = 0; i < unconfirmedTransactions.length; i++) {
                rows += NRS.getTransactionRowHTML(unconfirmedTransactions[i], false, decimals);
            }
        }

        NRS.sendRequest("getBlockchainTransactions+", params, function(response) {
            if (response.transactions && response.transactions.length) {
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
                for (var i = 0; i < response.transactions.length; i++) {
                    var transaction = response.transactions[i];
                    transaction.confirmed = true;
                    rows += NRS.getTransactionRowHTML(transaction, false, decimals);
                }

                NRS.dataLoaded(rows);
                NRS.addPhasingInfoToTransactionRows(response.transactions);
            } else {
                NRS.dataLoaded(rows);
            }
        });
    };

	NRS.incoming.dashboard = function() {
		NRS.loadPage("dashboard");
	};

	var isHoldingEntry = function (entry){
		return /ASSET_BALANCE/i.test(entry.holdingType) || /CURRENCY_BALANCE/i.test(entry.holdingType);
	};

    NRS.getLedgerNumberOfDecimals = function (entries){
		var decimalParams = {};
		decimalParams.changeDecimals = NRS.getNumberOfDecimals(entries, "change", function(entry) {
			if (isHoldingEntry(entry)) {
				return "";
			}
			return NRS.formatAmount(entry.change);
		});
		decimalParams.holdingChangeDecimals = NRS.getNumberOfDecimals(entries, "change", function(entry) {
			if (isHoldingEntry(entry)) {
				return NRS.formatQuantity(entry.change, entry.holdingInfo.decimals);
			}
			return "";
		});
		decimalParams.balanceDecimals = NRS.getNumberOfDecimals(entries, "balance", function(entry) {
			if (isHoldingEntry(entry)) {
				return "";
			}
			return NRS.formatAmount(entry.balance);
		});
		decimalParams.holdingBalanceDecimals = NRS.getNumberOfDecimals(entries, "balance", function(entry) {
			if (isHoldingEntry(entry)) {
				return NRS.formatQuantity(entry.balance, entry.holdingInfo.decimals);
			}
			return "";
		});
		return decimalParams;
	};

    NRS.pages.ledger = function() {
		var rows = "";
        var params = {
            "account": NRS.account,
            "includeHoldingInfo": true,
            "firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
            "lastIndex": NRS.pageNumber * NRS.itemsPerPage
        };

        NRS.sendRequest("getAccountLedger+", params, function(response) {
            if (response.entries && response.entries.length) {
                if (response.entries.length > NRS.itemsPerPage) {
                    NRS.hasMorePages = true;
                    response.entries.pop();
                }
				var decimalParams = NRS.getLedgerNumberOfDecimals(response.entries);
                for (var i = 0; i < response.entries.length; i++) {
                    var entry = response.entries[i];
                    rows += NRS.getLedgerEntryRow(entry, decimalParams);
                }
            }
            NRS.dataLoaded(rows);
			if (NRS.ledgerTrimKeep > 0) {
				var ledgerMessage = $("#account_ledger_message");
                ledgerMessage.text($.t("account_ledger_message", { blocks: NRS.ledgerTrimKeep }));
				ledgerMessage.show();
			}
        });
	};

	NRS.pages.transactions = function(callback, subpage) {
        var typeNavi = $('#transactions_type_navi');
        if (typeNavi.children().length == 0) {
			NRS.buildTransactionsTypeNavi();
			NRS.buildTransactionsSubTypeNavi();
		}

		if (subpage) {
			typeNavi.find('li a[data-transaction-type="' + subpage + '"]').click();
			return;
		}

		var selectedType = typeNavi.find('li.active a').attr('data-transaction-type');
		var selectedSubType = $('#transactions_sub_type_navi').find('li.active a').attr('data-transaction-sub-type');
		if (!selectedSubType) {
			selectedSubType = "";
		}
		if (selectedType == "unconfirmed") {
			NRS.displayUnconfirmedTransactions(NRS.account);
			return;
		}
		if (selectedType == "phasing") {
			NRS.displayPhasedTransactions();
			return;
		}
		if (selectedType == "all_unconfirmed") {
			NRS.displayUnconfirmedTransactions("");
			return;
		}

		var rows = "";
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		};
        var unconfirmedTransactions;
		if (selectedType) {
			params.type = selectedType;
			params.subtype = selectedSubType;
			unconfirmedTransactions = NRS.getUnconfirmedTransactionsFromCache(params.type, (params.subtype ? params.subtype : []));
		} else {
			unconfirmedTransactions = NRS.unconfirmedTransactions;
		}
		var decimals = NRS.getTransactionsAmountDecimals(unconfirmedTransactions);
		if (unconfirmedTransactions) {
			for (var i = 0; i < unconfirmedTransactions.length; i++) {
				rows += NRS.getTransactionRowHTML(unconfirmedTransactions[i], false, decimals);
			}
		}

		NRS.sendRequest("getBlockchainTransactions+", params, function(response) {
			if (response.transactions && response.transactions.length) {
				if (response.transactions.length > NRS.itemsPerPage) {
					NRS.hasMorePages = true;
					response.transactions.pop();
				}
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
					transaction.confirmed = true;
					rows += NRS.getTransactionRowHTML(transaction, false, decimals);
				}

				NRS.dataLoaded(rows);
				NRS.addPhasingInfoToTransactionRows(response.transactions);
        var trxTable = $('#transactions_table');
        if (NRS.compactTables == 1) {
          trxTable.find("thead tr").children(":nth-child(2)").hide();
          trxTable.find("thead tr").children(":nth-child(8)").hide();

        } else {
          trxTable.find("thead tr").children(":nth-child(2)").show();
          trxTable.find("thead tr").children(":nth-child(8)").show();

        }
			} else {
				NRS.dataLoaded(rows);
			}
		});
	};

  NRS.pages.hierarchy = function(callback, subpage) {
		var rows = "";
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage
		};

    NRS.sendRequest("getAccountChildren", params, function(response) {
        var infoModalTransactionsTable = $("#user_info_modal_hierarchy_table");
        if (response.children && response.children.length) {
          if (response.children.length > NRS.itemsPerPage) {
  					NRS.hasMorePages = true;
  				}
          var rows = "";
          var amountDecimals = NRS.getNumberOfDecimals(response.children, "amountNQT", function(val) {
            return NRS.formatAmount(val.amountNQT);
          });
          var feeDecimals = NRS.getNumberOfDecimals(response.children, "balanceNQT", function(val) {
            return NRS.formatAmount(val.fee);
          });
          for (var i = 0; i < response.children.length && i < NRS.itemsPerPage; i++) {
            var child = response.children[i];
            // if (child.amountNQT) {
            //   child.amount = new BigInteger(child.amountNQT);
            //   child.balance = new BigInteger(child.balanceNQT);
            // }
            rows += "<tr data-sublevel='0'>" +
              "<td>" +
              (child.childCount>0?"<button type='button' class='btn-dropdown' onclick='NRS.expand(\""+String(child.accountRS)+"\", this)'><i class='fa fa-angle-right fa-lg'></i></button> ":"<button type='button' class='btn-dropdown' style='pointer-events:none'><i class='fa fa-angle-right fa-lg' style='visibility:hidden'></i></button> ") +
              "<a href='#' data-user='" + String(child.accountRS).escapeHTML() + "' id='account_link_in_sidebar' class='show_account_modal_action user-info'>" + (child.name != null?(String(child.name).length>0?"<i class='fa fa-user'></i> "+unescape(child.name):child.accountRS):child.accountRS) + "</a></td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.balanceNQT/100, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.amountNQT/100, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + (child.childCount>0?child.childCount:"") + "</td>" +
              "<td>" + (child.forging?"+":"") + "</td>" +
            "</tr>";
            child.confirmed = true;
          }

          $("#hierarchy_page > section > div > div").attachDragger();

          infoModalTransactionsTable.find("tbody").empty().append(rows);
          NRS.dataLoaded(rows);
        } else {
          infoModalTransactionsTable.find("tbody").empty();
          NRS.dataLoaded(rows);
        }
    });
	};

  NRS.hierarchyRows = function(accountRS, pageNumber, sublevel, cb) {
    var rows = "";
		var params = {
			"account": accountRS,
			"firstIndex": pageNumber * 100 - 100,
      "lastIndex": pageNumber * 100
		};
    NRS.sendRequest("getAccountChildren", params, function(response) {
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
            var spacer = "";
            for (var spacers = 0; spacers < sublevel; spacers++)
              spacer = spacer + "<button type='button' class='hierarchy-spacer "+NRS.nextColor(spacers+1)+"'>&nbsp;</button>";
            rows += "<tr data-sublevel='"+sublevel+"' style='transition:0.3s all linear;opacity:0'>" +
              // "<td style='border-left: "+(sublevel*4)+"px solid red;padding-left: "+(sublevel*20+8)+"px;'>"+
              "<td>"+ spacer +
              (child.childCount>0?"<button type='button' class='btn-dropdown' onclick='NRS.expand(\""+String(child.accountRS)+"\", this)'><i class='fa fa-angle-right fa-lg'></i></button>":"<button type='button' class='btn-dropdown'><i class='fa fa-angle-right fa-lg' style='visibility:hidden'></i></button>")+
              " <a href='#' data-user='" + String(child.accountRS).escapeHTML() + "' id='account_link_in_sidebar' class='show_account_modal_action user-info'>" + (child.name != null?(String(child.name).length>0?"<i class='fa fa-user'></i> "+unescape(child.name):child.accountRS):child.accountRS) + "</a></td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.balanceNQT/100, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.amountNQT/100, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + (child.childCount>0?child.childCount:"") + "</td>" +
              "<td>" + (child.forging?"+":"") + "</td>" +
            "</tr>";
            child.confirmed = true;
          }
          cb(rows);
        }
    });
  }

  NRS.expand = function(accountRS, row) {
    var t = $("#user_info_modal_hierarchy_table");
    var tr = $(row).closest("tr");
    var icon = $(row).children("i");
    var sublevel = tr.data("sublevel") + 1;
    var button = tr.children("td:first-child").find("button.btn-dropdown");
    t.removeClass("dropdown-upround")
    let toremove = [];
    tr.siblings().each(function(){
      sib = $(this);
      var sibicon = sib.children("td:first-child").find("i").first();
      var sibbutton = sib.children("td:first-child").find("button.btn-dropdown");
      if (sib.attr("data-sublevel") >= sublevel) {
        sib.css("opacity", "0");
        sib.removeClass("expanded");
        sib.children("td").children(".btn-dropdown").removeClass("color1");
        sib.children("td").children(".btn-dropdown").removeClass("color2");
        sib.children("td").children(".btn-dropdown").removeClass("color3");
        sib.children("td").children(".btn-dropdown").removeClass("color4");
        sib.data("dying", true);
        toremove.push(sib);
      } else if (sib.attr("data-sublevel") == sublevel-1) {
        if (sibicon.hasClass("fa-angle-down")) {
          sibicon.removeClass("fa-angle-down");
          sibicon.addClass("fa-angle-right");
          sibbutton.removeClass("color1");
          sibbutton.removeClass("color2");
          sibbutton.removeClass("color3");
          sibbutton.removeClass("color4");
        }
        sib.removeClass("dropdown-upround");
      }
    });
    if (toremove && toremove.length > 0) {
      setTimeout(function(){
        var i;
        for (i = 0; i < toremove.length; i++) {
          $(toremove[i]).remove();
        }
      },200);
    }
    if (icon.hasClass("fa-angle-right")) {
      icon.removeClass("fa-angle-right");
      icon.addClass("fa-angle-down");
      tr.addClass("expanded");
      $(row).addClass(NRS.nextColor(sublevel));
      NRS.hierarchyRows(accountRS, 1, sublevel, function(rows){
        var next = tr.next();
        tr.after(rows);
        next.addClass("dropdown-upround");
        setTimeout(function(){
          tr.siblings().each(function(){
            sib = $(this);
            var sibicon = sib.children("td:first-child").find("i");
            if (sibicon.hasClass("fa-angle-down")) {
              sib.addClass("expanded");
            } else {
              sib.removeClass("expanded");
            }
            var siblevel = sib.attr("data-sublevel");
            if (siblevel == sublevel && sib.data("dying") != true) {
              // sib.children("td").children(".btn-dropdown").css("margin-left", (sublevel*20)+"px")
            }
            sib.css("opacity", "1");
          });
        }, 50);

      });
    } else {
      icon.removeClass("fa-angle-down");
      icon.addClass("fa-angle-right");
      tr.removeClass("expanded");
      button.css("background", "none");
      button.removeClass("color1");
      button.removeClass("color2");
      button.removeClass("color3");
      button.removeClass("color4");
    }

    var headForging = $("#hierarchy_table>thead>tr>th:last-child");
    var headDisciples = $("#hierarchy_table>thead>tr>th:nth-child(4)");
    if (sublevel > 0 && !NRS.isHierarchyTableMinified) {
      // Minify
      NRS.isHierarchyTableMinified = true;
      headForging.html("FRG");
      headDisciples.html("#");
    } else if (sublevel == 1 && NRS.isHierarchyTableMinified) {
      // Cancel minification
      NRS.isHierarchyTableMinified = false;
      headForging.html($.t("forging"));
      headDisciples.html($.t("disciples"));
    }
  }

	NRS.updateApprovalRequests = function() {
		var params = {
			"account": NRS.account,
			"firstIndex": 0,
			"lastIndex": 20
		};
		NRS.sendRequest("getVoterPhasedTransactions", params, function(response) {
			var $badge = $('#dashboard_link').find('.sm_treeview_submenu a[data-page="approval_requests_account"] span.badge');
			if (response.transactions && response.transactions.length) {
				if (response.transactions.length == 0) {
					$badge.hide();
				} else {
                    var length;
					if (response.transactions.length == 21) {
						length = "20+";
					} else {
						length = String(response.transactions.length);
					}
					$badge.text(length);
					$badge.show();
				}
			} else {
				$badge.hide();
			}
		});
		if (NRS.currentPage == 'approval_requests_account') {
			NRS.loadPage(NRS.currentPage);
		}
	};

	NRS.pages.approval_requests_account = function() {
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		};
		NRS.sendRequest("getVoterPhasedTransactions", params, function(response) {
			var rows = "";

			if (response.transactions && response.transactions.length) {
				if (response.transactions.length > NRS.itemsPerPage) {
					NRS.hasMorePages = true;
					response.transactions.pop();
				}
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var t = response.transactions[i];
					t.confirmed = true;
					rows += NRS.getTransactionRowHTML(t, ['approve'], decimals);
				}
			}
			NRS.dataLoaded(rows);
			NRS.addPhasingInfoToTransactionRows(response.transactions);
		});
	};

	NRS.incoming.transactions = function() {
		NRS.loadPage("transactions");
	};

	NRS.setup.transactions = function() {
		var sidebarId = 'dashboard_link';


		var options = {
			"id": 'account_info_button',
			"titleHTML": '<i class="fa fa-dashboard"></i> <span data-i18n="dashboard">Dashboard</span>',
			"page": 'dashboard',
			"desiredPosition": 10,
		};
		NRS.addSimpleSidebarMenuItem(options);

		options = {
			"id": 'contacts_button_sidebar',
			"titleHTML": '<i class="fa fa-users"></i> <span data-i18n="contacts">Contacts</span>',
			"page": 'contacts',
			"desiredPosition": 14,
		};
		NRS.addSimpleSidebarMenuItem(options);

		NRS.addSpacerToSidebar("Account", 16);

		options = {
			"id": 'account_ledger_button',
			"titleHTML": '<i class="fa fa-table"></i> <span data-i18n="account_ledger">Account Ledger</span>',
			"page": 'ledger',
			"desiredPosition": 20,
		};
		NRS.addSimpleSidebarMenuItem(options);

		options = {
			"id": 'my_transactions_button',
			"titleHTML": '<i class="fa fa-exchange"></i> <span data-i18n="my_transactions">My Transactions</span>',
			"page": 'transactions',
			"desiredPosition": 30,
		};
		NRS.addSimpleSidebarMenuItem(options);
    NRS.getDiscoveryState(function(state){
      if (state == NRS.DISCOVERY_PUBLIC) {
        var options2 = {
          "id": 'my_hierarchy_button',
          "titleHTML": '<i class="fa fa-sitemap"></i> <span data-i18n="my_disciples">My Disciples</span>',
          "page": 'hierarchy',
          "desiredPosition": 31,
        };
        NRS.addSimpleSidebarMenuItem(options2);
      }
    });

		if (NRS.isDiscoveryAvailableToLaunch()) {
			options = {
				"id": 'discovery_button',
				"titleHTML": '<i class="fa fa-search"></i> <span>Discovery</span>',
				"page": 'discovery',
				"desiredPosition": 32,
			};
			NRS.addSimpleSidebarMenuItem(options);

			if (NRS.discoveryState == NRS.DISCOVERY_PUBLIC) {
				$('#discovery_admin_password').hide();
				$('#discovery_admin_password_label').hide();
			} else {
					var input = document.getElementById("discovery_admin_password");
					input.addEventListener("keyup", function(event) {
				  event.preventDefault();
				  if (event.keyCode === 13) {
				    document.getElementById("discovery_start_button").click();
				  }
				});
			}

			$('#discovery_start_button').on('click', function(e){
				  var discoveryLaunchArguments = '+account ' + NRS.accountRS + (NRS.discoveryState!=NRS.DISCOVERY_PUBLIC?' +adminPassword '+$('#discovery_admin_password').val():'');
					var started = java.startNativeModule('aerial', discoveryLaunchArguments); //TODO ADMIN PASSWORD
					if (started)
										$.growl('Starting Aerial...');
					else {
						$.growl('Failed to start Aerial!');
					}
			});
		} else {
			// if (NRS.discoveryState == NRS.DISCOVERY_PUBLIC) {
			// 	options = {
			// 		"id": 'discovery_button',
			// 		"titleHTML": '<i class="fa fa-search"></i> <span>Discovery</span>',
			// 		"page": 'discovery_webgl',
			// 		"desiredPosition": 31,
			// 	};
			// 	NRS.addSimpleSidebarMenuItem(options);
			// } else if (NRS.discoveryState == NRS.DISCOVERY_PROTECTED) {
			// 	options = {
			// 		"id": 'discovery_button',
			// 		"titleHTML": '<i class="fa fa-search"></i> <span>Discovery</span>',
			// 		"page": 'discovery_webgl_protected',
			// 		"desiredPosition": 31,
			// 	};
			// 	NRS.addSimpleSidebarMenuItem(options);
			// }
		}

		NRS.addSpacerToSidebar("Blockchain", 35);

		options = {
			"id": 'blocks_button',
			"titleHTML": '<i class="fa fa-chain"></i> <span>Blockchain Explorer</span>',
			"page": 'blocks',
			"desiredPosition": 40,
		};
		NRS.addSimpleSidebarMenuItem(options);

		options = {
			"id": 'peers_button',
			"titleHTML": '<i class="fa fa-th-list"></i> <span>Peers</span>',
			"page": 'peers',
			"desiredPosition": 41,
		};
		NRS.addSimpleSidebarMenuItem(options);

	};

	$(document).on("click", "#transactions_type_navi li a", function(e) {
		e.preventDefault();
		$('#transactions_type_navi').find('li.active').removeClass('active');
  		$(this).parent('li').addClass('active');
  		NRS.buildTransactionsSubTypeNavi();
  		NRS.pageNumber = 1;
		NRS.loadPage("transactions");
	});

	$(document).on("click", "#transactions_sub_type_navi li a", function(e) {
		e.preventDefault();
		$('#transactions_sub_type_navi').find('li.active').removeClass('active');
  		$(this).parent('li').addClass('active');
  		NRS.pageNumber = 1;
		NRS.loadPage("transactions");
	});

	$(document).on("click", "#transactions_sub_type_show_hide_btn", function(e) {
		e.preventDefault();
        var subTypeNaviBox = $('#transactions_sub_type_navi_box');
        if (subTypeNaviBox.is(':visible')) {
			subTypeNaviBox.hide();
			$(this).text($.t('show_type_menu', 'Show Type Menu'));
		} else {
			subTypeNaviBox.show();
			$(this).text($.t('hide_type_menu', 'Hide Type Menu'));
		}
	});

	return NRS;
}(NRS || {}, jQuery));
