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
 * @depends {3rdparty/jquery-2.1.0.js}
 * @depends {3rdparty/bootstrap.js}
 * @depends {3rdparty/big.js}
 * @depends {3rdparty/jsbn.js}
 * @depends {3rdparty/jsbn2.js}
 * @depends {3rdparty/pako.js}
 * @depends {3rdparty/webdb.js}
 * @depends {3rdparty/growl.js}
 * @depends {crypto/curve25519.js}
 * @depends {crypto/curve25519_.js}
 * @depends {crypto/passphrasegenerator.js}
 * @depends {crypto/sha256worker.js}
 * @depends {crypto/3rdparty/cryptojs/aes.js}
 * @depends {crypto/3rdparty/cryptojs/sha256.js}
 * @depends {crypto/3rdparty/jssha256.js}
 * @depends {util/converters.js}
 * @depends {util/extensions.js}
 * @depends {util/prizmaddress.js}
 */

var NRS = (function(NRS, $, undefined) {
	"use strict";
	NRS.isOnFork = false;
	NRS.server = "";
	NRS.state = {};
	NRS.blocks = [];
	NRS.account = "";
	NRS.accountRS = "";
	NRS.publicKey = "";
	NRS.accountInfo = {};
	NRS.discoveryServer = "";
	NRS.discoveryAerialURL = "http://94.130.167.158/discovery/Aerial/Embedded/page.html";
	NRS.database = null;
	NRS.databaseSupport = false;
	NRS.databaseFirstStart = false;

	// Legacy database, don't use this for data storage
	NRS.legacyDatabase = null;
	NRS.legacyDatabaseWithData = false;

	NRS.serverConnect = false;
	NRS.peerConnect = false;

	NRS.settings = {};
	NRS.contacts = {};

	NRS.isTestNet = false;
	NRS.isLocalHost = false;
	NRS.forgingStatus = NRS.constants.UNKNOWN;
	NRS.isAccountForging = false;
	NRS.isLeased = false;
	NRS.needsAdminPassword = true;
    NRS.upnpExternalAddress = null;
	NRS.ledgerTrimKeep = 0;

	NRS.lastBlockHeight = 0;
	NRS.downloadingBlockchain = false;

	NRS.rememberPassword = false;
	NRS.discoveryState = NRS.DISCOVERY_UNAVAILABLE;
	NRS.selectedContext = null;

	NRS.currentPage = "dashboard";
	NRS.currentSubPage = "";
	NRS.pageNumber = 1;
	//NRS.itemsPerPage = 50;  /* Now set in nrs.settings.js */

	NRS.pages = {};
	NRS.incoming = {};
	NRS.setup = {};

	NRS.appVersion = "";
	NRS.appPlatform = "";
	NRS.assetTableKeys = [];

	var stateInterval;
	var stateIntervalSeconds = 30;
	var isScanning = false;

    NRS.init = function() {
		NRS.sendRequest("getState", {
			"includeCounts": "false"
		}, function (response) {
			var isTestnet = false;
			var isOffline = false;
			var peerPort = 0;
			for (var key in response) {
                if (!response.hasOwnProperty(key)) {
                    continue;
                }
				if (key == "isTestnet") {
					isTestnet = response[key];
				}
				if (key == "isOffline") {
					isOffline = response[key];
				}
				if (key == "peerPort") {
					peerPort = response[key];
				}
				if (key == "needsAdminPassword") {
					NRS.needsAdminPassword = response[key];
				}
				if (key == "upnpExternalAddress") {
                    NRS.upnpExternalAddress = response[key];
				}
			}

			if (!isTestnet) {
				$(".testnet_only").hide();
			} else {
				NRS.isTestNet = true;
				var testnetWarningDiv = $("#testnet_warning");
				var warningText = testnetWarningDiv.text() + " The testnet peer port is " + peerPort + (isOffline ? ", the peer is working offline." : ".");
                NRS.logConsole(warningText);
				testnetWarningDiv.text(warningText);
				$(".testnet_only, #testnet_login, #testnet_warning").show();
			}
			NRS.loadServerConstants();
			NRS.initializePlugins();
      NRS.printEnvInfo();
			NRS.getDiscoveryState(function(state){
			NRS.discoveryState = state;
				console.log("DiscoveryAPI: " + state);
			});
		});

		if (!NRS.server) {
			NRS.discoveryServer = window.location.hostname.toLowerCase();
			NRS.isLocalHost = NRS.discoveryServer == "localhost" || NRS.discoveryServer == "127.0.0.1" || NRS.isPrivateIP(NRS.discoveryServer);
            NRS.logProperty("NRS.isLocalHost");
		}

		if (!NRS.isLocalHost) {
			$(".remote_warning").show();
		}
		var hasLocalStorage = false;
		try {
			//noinspection BadExpressionStatementJS
            window.localStorage && localStorage;
			hasLocalStorage = checkLocalStorage();
		} catch (err) {
			NRS.logConsole("localStorage is disabled, error " + err.message);
			hasLocalStorage = false;
		}

		if (!hasLocalStorage) {
			NRS.logConsole("localStorage is disabled, cannot load wallet");
			// TODO add visible warning
			return; // do not load client if local storage is disabled
		}

		if(!(navigator.userAgent.indexOf('Safari') != -1 &&
			navigator.userAgent.indexOf('Chrome') == -1) &&
			navigator.userAgent.indexOf('JavaFX') == -1) {
			// Don't use account based DB in Safari due to a buggy indexedDB implementation (2015-02-24)
			NRS.createLegacyDatabase();
		}

		if (NRS.getStrItem("remember_passphrase")) {
			$("#remember_password").prop("checked", true);
		}
		NRS.getSettings(false);

		NRS.getState(function() {
			setTimeout(function() {
//				NRS.checkAliasVersions();
			}, 5000);
		});

		$("body").popover({
			"selector": ".show_popover",
			"html": true,
			"trigger": "hover"
		});

		NRS.showLockscreen();
		NRS.setStateInterval(30);

		setInterval(NRS.checkAliasVersions, 1000 * 60 * 60);

		NRS.allowLoginViaEnter();
		NRS.automaticallyCheckRecipient();

		$("#dashboard_table, #transactions_table").on("mouseenter", "td.confirmations", function() {
			$(this).popover("show");
		}).on("mouseleave", "td.confirmations", function() {
			$(this).popover("destroy");
			$(".popover").remove();
		});

		_fix();

		$(window).on("resize", function() {
			_fix();

			if (NRS.currentPage == "asset_exchange") {
				NRS.positionAssetSidebar();
			}
		});
		// Enable all static tooltip components
		// tooltip components generated dynamically (for tables cells for example)
		// has to be enabled by activating this code on the specific widget
		$("[data-toggle='tooltip']").tooltip();

		$("#dgs_search_account_center").mask("PRIZM-****-****-****-*****");

		if (NRS.getUrlParameter("account")){
			NRS.login(false,NRS.getUrlParameter("account"));
		}
	};

  $.fn.attachDragger = function(){
    var attachment = false, lastPosition, position, difference;
    $( $(this).selector ).on("mousedown mouseup mousemove",function(e){
        if( e.type == "mousedown" ) attachment = true, lastPosition = [e.clientX, e.clientY];
        if( e.type == "mouseup" ) attachment = false;
        if( e.type == "mousemove" && attachment == true ){
            position = [e.clientX, e.clientY];
            difference = [ (position[0]-lastPosition[0]), (position[1]-lastPosition[1]) ];
            $(this).scrollLeft( $(this).scrollLeft() - difference[0]*0.5 );
            $(this).scrollTop( $(this).scrollTop() - difference[1]*0.5 );
            lastPosition = [e.clientX, e.clientY];
        }
    });
    $(window).on("mouseup", function(){
        attachment = false;
    });
  }

  NRS.darkPrizm = function () {
    if (s.length > 0) {
      console.log("Enabled day theme");
      s.remove();
    } else {
      console.log("Enabled night theme");
      $('head').append('<link rel="stylesheet" type="text/css" id="darkprizm" href="css/darkprizm.css">');
    }
  }

	function _fix() {
		var height = $(window).height() - $("body > .header").height();
		var content = $(".wrapper").height();

		$(".content.content-stretch:visible").width($(".page:visible").width());
		if (content > height) {
			$(".left-side, html, body").css("min-height", content + "px");
		} else {
			$(".left-side, html, body").css("min-height", height + "px");
		}
	}

	NRS.setStateInterval = function(seconds) {
		if (!NRS.isPollGetState()) {
			return;
		}
		if (seconds == stateIntervalSeconds && stateInterval) {
			return;
		}
		if (stateInterval) {
			clearInterval(stateInterval);
		}
		stateIntervalSeconds = seconds;
		stateInterval = setInterval(function() {
			NRS.getState(null);
			NRS.updateForgingStatus();
		}, 1000 * seconds);
	};

	var _firstTimeAfterLoginRun = false;

	NRS.getState = function(callback, msg) {
		if (msg) {
			NRS.logConsole("getState event " + msg);
		}
		NRS.sendRequest("getBlockchainStatus", {}, function(response) {
			if (response.errorCode) {
				NRS.serverConnect = false;
                $.growl($.t("server_connection_error") + " " + response.errorDescription.escapeHTML());
			} else {
				var firstTime = !("lastBlock" in NRS.state);
				var previousLastBlock = (firstTime ? "0" : NRS.state.lastBlock);

				NRS.state = response;
				NRS.serverConnect = true;
				NRS.ledgerTrimKeep = response.ledgerTrimKeep;
				$("#sidebar_block_link").html(NRS.getBlockLink(NRS.state.numberOfBlocks - 1));
				if (firstTime) {
					$("#nrs_version").html(NRS.state.version).removeClass("loading_dots");
					NRS.getBlock(NRS.state.lastBlock, NRS.handleInitialBlocks);
				} else if (NRS.state.isScanning) {
					//do nothing but reset NRS.state so that when isScanning is done, everything is reset.
					isScanning = true;
				} else if (isScanning) {
					//rescan is done, now we must reset everything...
					isScanning = false;
					NRS.blocks = [];
					NRS.tempBlocks = [];
					NRS.getBlock(NRS.state.lastBlock, NRS.handleInitialBlocks);
					if (NRS.account) {
						NRS.getInitialTransactions();
						NRS.getAccountInfo();
					}
				} else if (previousLastBlock != NRS.state.lastBlock) {
					NRS.tempBlocks = [];
					if (NRS.account) {
						NRS.getAccountInfo();
					}
					NRS.getBlock(NRS.state.lastBlock, NRS.handleNewBlocks);
					if (NRS.account) {
						NRS.getNewTransactions();
						NRS.updateApprovalRequests();
					}
				} else {
					if (NRS.account) {
						NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							NRS.handleIncomingTransactions(unconfirmedTransactions, false);
						});
					}
				}
				if (NRS.account && !_firstTimeAfterLoginRun) {
					//Executed ~30 secs after login, can be used for tasks needing this condition state
					_firstTimeAfterLoginRun = true;
				}

				if (callback) {
					callback();
				}
			}
			/* Checks if the client is connected to active peers */
			NRS.checkConnected();
			//only done so that download progress meter updates correctly based on lastFeederHeight
			if (NRS.downloadingBlockchain) {
				NRS.updateBlockchainDownloadProgress();
			}
		});
	};

	$("#logo, .sidebar-menu").on("click", "a", function(e, data) {
		if ($(this).hasClass("ignore")) {
			$(this).removeClass("ignore");
			return;
		}

		e.preventDefault();

		if ($(this).data("toggle") == "modal") {
			return;
		}

		var page = $(this).data("page");

		// Discovery Aerial Background Frame Draw Errors Fix
		if (page == NRS.currentPage) {
			if (data && data.callback) {
				data.callback();
			}
			return;
		}

		$(".page").hide();

		$(document.documentElement).scrollTop(0);

		$("#" + page + "_page").show();

		$(".content-header h1").find(".loading_dots").remove();

        var $newActiveA;
        if ($(this).attr("id") && $(this).attr("id") == "logo") {
            $newActiveA = $("#dashboard_link").find("a");
		} else {
			$newActiveA = $(this);
		}
		var $newActivePageLi = $newActiveA.closest("li.treeview");

		$("ul.sidebar-menu > li.active").each(function(key, elem) {
			if ($newActivePageLi.attr("id") != $(elem).attr("id")) {
				$(elem).children("a").first().addClass("ignore").click();
			}
		});

		$("ul.sidebar-menu > li.sm_simple").removeClass("active");
		if ($newActiveA.parent("li").hasClass("sm_simple")) {
			$newActiveA.parent("li").addClass("active");
		}

		$("ul.sidebar-menu li.sm_treeview_submenu").removeClass("active");
		if($(this).parent("li").hasClass("sm_treeview_submenu")) {
			$(this).closest("li").addClass("active");
		}

		if (NRS.currentPage != "messages") {
			$("#inline_message_password").val("");
		}

		//NRS.previousPage = NRS.currentPage;
		NRS.currentPage = page;
		NRS.currentSubPage = "";
		NRS.pageNumber = 1;
		NRS.showPageNumbers = false;

		if (NRS.pages[page]) {
			NRS.pageLoading();
			NRS.resetNotificationState(page);
            var callback;
            if (data) {
				if (data.callback) {
					callback = data.callback;
				} else {
					callback = data;
				}
			} else {
				callback = undefined;
			}
            var subpage;
            if (data && data.subpage) {
                subpage = data.subpage;
			} else {
				subpage = undefined;
			}
			NRS.pages[page](callback, subpage);
		}
	});

	$("button.goto-page, a.goto-page").click(function(event) {
		event.preventDefault();
		NRS.goToPage($(this).data("page"), undefined, $(this).data("subpage"));
	});

	NRS.loadPage = function(page, callback, subpage) {
		NRS.pageLoading();
		NRS.pages[page](callback, subpage);
	};

	NRS.goToPage = function(page, callback, subpage) {
		var $link = $("ul.sidebar-menu a[data-page=" + page + "]");

		if ($link.length > 1) {
			if ($link.last().is(":visible")) {
				$link = $link.last();
			} else {
				$link = $link.first();
			}
		}

		if ($link.length == 1) {
			$link.trigger("click", [{
				"callback": callback,
				"subpage": subpage
			}]);
			NRS.resetNotificationState(page);
		} else {
			NRS.currentPage = page;
			NRS.currentSubPage = "";
			NRS.pageNumber = 1;
			NRS.showPageNumbers = false;

			$("ul.sidebar-menu a.active").removeClass("active");
			$(".page").hide();
			$("#" + page + "_page").show();
			if (NRS.pages[page]) {
				NRS.pageLoading();
				NRS.resetNotificationState(page);
				NRS.pages[page](callback, subpage);
			}
		}
	};

	NRS.pageLoading = function() {
		NRS.hasMorePages = false;

		var $pageHeader = $("#" + NRS.currentPage + "_page .content-header h1");
		$pageHeader.find(".loading_dots").remove();
		$pageHeader.append("<span class='loading_dots'><span>.</span><span>.</span><span>.</span></span>");
	};

	NRS.pageLoaded = function(callback) {
		var $currentPage = $("#" + NRS.currentPage + "_page");

		$currentPage.find(".content-header h1 .loading_dots").remove();

		if ($currentPage.hasClass("paginated")) {
			NRS.addPagination();
		}

		if (callback) {
			try {
                callback();
            } catch(e) { /* ignore since sometimes callback is not a function */ }
		}
	};

NRS.addPagination = function () {
        var firstStartNr = 1;
		var firstEndNr = NRS.itemsPerPage;
		var currentStartNr = (NRS.pageNumber-1) * NRS.itemsPerPage + 1;
		var currentEndNr = NRS.pageNumber * NRS.itemsPerPage;

		var prevHTML = '<span style="display:inline-block;width:48px;text-align:right;">';
		var firstHTML = '<span style="display:inline-block;min-width:48px;text-align:right;vertical-align:top;margin-top:4px;">';
		var currentHTML = '<span style="display:inline-block;min-width:48px;text-align:left;vertical-align:top;margin-top:4px;">';
		var nextHTML = '<span style="display:inline-block;width:48px;text-align:left;">';

		if (NRS.pageNumber > 1) {
			prevHTML += "<a href='#' data-page='" + (NRS.pageNumber - 1) + "' title='" + $.t("previous") + "' style='font-size:20px;'>";
			prevHTML += "<i class='fa fa-arrow-circle-left'></i></a>";
		} else {
			prevHTML += '&nbsp;';
		}

		if (NRS.hasMorePages) {
			currentHTML += currentStartNr + "-" + currentEndNr + "&nbsp;";
			nextHTML += "<a href='#' data-page='" + (NRS.pageNumber + 1) + "' title='" + $.t("next") + "' style='font-size:20px;'>";
			nextHTML += "<i class='fa fa-arrow-circle-right'></i></a>";
		} else {
			if (NRS.pageNumber > 1) {
				currentHTML += currentStartNr + "+";
			} else {
				currentHTML += "&nbsp;";
			}
			nextHTML += "&nbsp;";
		}
		if (NRS.pageNumber > 1) {
			firstHTML += "&nbsp;<a href='#' data-page='1'>" + firstStartNr + "-" + firstEndNr + "</a>&nbsp;|&nbsp;";
		} else {
			firstHTML += "&nbsp;";
		}

		prevHTML += '</span>';
		firstHTML += '</span>';
		currentHTML += '</span>';
		nextHTML += '</span>';

		var output = prevHTML + firstHTML + currentHTML + nextHTML;
		var $paginationContainer = $("#" + NRS.currentPage + "_page .data-pagination");

		if ($paginationContainer.length) {
			$paginationContainer.html(output);
		}
	};

	$(document).on("click", ".data-pagination a", function(e) {
		e.preventDefault();

		NRS.goToPageNumber($(this).data("page"));
	});

	NRS.goToPageNumber = function(pageNumber) {
		/*if (!pageLoaded) {
			return;
		}*/
		NRS.pageNumber = pageNumber;

		NRS.pageLoading();

		NRS.pages[NRS.currentPage]();
	};

	function initUserDB() {
		NRS.storageSelect("data", [{
			"id": "asset_exchange_version"
		}], function(error, result) {
			if (!result || !result.length) {
				NRS.storageDelete("assets", [], function(error) {
					if (!error) {
						NRS.storageInsert("data", "id", {
							"id": "asset_exchange_version",
							"contents": 2
						});
					}
				});
			}
		});

		NRS.storageSelect("data", [{
			"id": "closed_groups"
		}], function(error, result) {
			if (result && result.length) {
//				NRS.setClosedGroups(result[0].contents.split("#"));
			} else {
				NRS.storageInsert("data", "id", {
					id: "closed_groups",
					contents: ""
				});
			}
		});
		NRS.loadContacts();
		NRS.getSettings(true);
		NRS.updateNotifications();
		NRS.setUnconfirmedNotifications();
		NRS.setPhasingNotifications();
		var page = NRS.getUrlParameter("page");
		if (page) {
			page = page.escapeHTML();
			if (NRS.pages[page]) {
				NRS.goToPage(page);
			} else {
				$.growl($.t("page") + " " + page + " " + $.t("does_not_exist"), {
					"type": "danger",
					"offset": 50
				});
			}
		}
		var modal = NRS.getUrlParameter("modal");
		if (modal) {
			modal = "#" + modal.escapeHTML();
			var urlParams = window.location.search.substring(1).split('&');
			if ($(modal)[0]) {
				var isValidParams = true;
				for (var i = 0; i < urlParams.length; i++) {
					var paramKeyValue = urlParams[i].split('=');
					if (paramKeyValue.length != 2) {
						continue;
					}
					var key = paramKeyValue[0].escapeHTML();
					if (key == "account" || key == "modal") {
						continue;
					}
					var value = paramKeyValue[1].escapeHTML();
                    var input = $(modal).find("input[name=" + key + "]");
                    if (input[0]) {
						if (input.attr("type") == "text") {
							input.val(value);
						} else if (input.attr("type") == "checkbox") {
							var isChecked = false;
							if (value != "true" && value != "false") {
								isValidParams = false;
								$.growl($.t("value") + " " + value + " " + $.t("must_be_true_or_false") + " " + $.t("for") + " " + key, {
									"type": "danger",
									"offset": 50
								});
							} else if (value == "true") {
								isChecked = true;
							}
							if (isValidParams) {
								input.prop('checked', isChecked);
							}
						}
					} else if ($(modal).find("textarea[name=" + key + "]")[0]) {
						$(modal).find("textarea[name=" + key + "]").val(decodeURI(value));
					} else {
						$.growl($.t("input") + " " + key + " " + $.t("does_not_exist_in") + " " + modal, {
							"type": "danger",
							"offset": 50
						});
						isValidParams = false;
					}
				}
				if (isValidParams) {
					$(modal).modal();
				}
			} else {
				$.growl($.t("modal") + " " + modal + " " + $.t("does_not_exist"), {
					"type": "danger",
					"offset": 50
				});
			}
		}
	}

	NRS.initUserDBSuccess = function() {
		NRS.databaseSupport = true;
		initUserDB();
        NRS.logConsole("IndexedDB initialized");
    };

	NRS.initUserDBWithLegacyData = function() {
		var legacyTables = ["contacts", "assets", "data"];
		$.each(legacyTables, function(key, table) {
			NRS.legacyDatabase.select(table, null, function(error, results) {
				if (!error && results && results.length >= 0) {
					NRS.database.insert(table, results, function(error, inserts) {});
				}
			});
		});
		setTimeout(function(){ NRS.initUserDBSuccess(); }, 1000);
	};

	NRS.initLocalStorage = function() {
		NRS.database = null;
		NRS.databaseSupport = false;
		initUserDB();
        NRS.logConsole("local storage initialized");
    };

	NRS.createLegacyDatabase = function() {
		var schema = {};
		var versionLegacyDB = 2;

		// Legacy DB before switching to account based DBs, leave schema as is
		schema["contacts"] = {
			id: {
				"primary": true,
				"autoincrement": true,
				"type": "NUMBER"
			},
			name: "VARCHAR(100) COLLATE NOCASE",
			email: "VARCHAR(200)",
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			description: "TEXT"
		};
		schema["assets"] = {
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			asset: {
				"primary": true,
				"type": "VARCHAR(25)"
			},
			description: "TEXT",
			name: "VARCHAR(10)",
			decimals: "NUMBER",
			quantityQNT: "VARCHAR(15)",
			groupName: "VARCHAR(30) COLLATE NOCASE"
		};
		schema["data"] = {
			id: {
				"primary": true,
				"type": "VARCHAR(40)"
			},
			contents: "TEXT"
		};
		if (versionLegacyDB == NRS.constants.DB_VERSION) {
			try {
				NRS.legacyDatabase = new WebDB("NRS_USER_DB", schema, versionLegacyDB, 4, function(error) {
					if (!error) {
						NRS.legacyDatabase.select("data", [{
							"id": "settings"
						}], function(error, result) {
							if (result && result.length > 0) {
								NRS.legacyDatabaseWithData = true;
							}
						});
					}
				});
			} catch (err) {
                NRS.logConsole("error creating database " + err.message);
			}
		}
	};

	function createSchema(){
		var schema = {};

		schema["contacts"] = {
			id: {
				"primary": true,
				"autoincrement": true,
				"type": "NUMBER"
			},
			name: "VARCHAR(100) COLLATE NOCASE",
			email: "VARCHAR(200)",
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			description: "TEXT"
		};
		schema["assets"] = {
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			asset: {
				"primary": true,
				"type": "VARCHAR(25)"
			},
			description: "TEXT",
			name: "VARCHAR(10)",
			decimals: "NUMBER",
			quantityQNT: "VARCHAR(15)",
			groupName: "VARCHAR(30) COLLATE NOCASE"
		};
		schema["polls"] = {
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			name: "VARCHAR(100)",
			description: "TEXT",
			poll: "VARCHAR(25)",
			finishHeight: "VARCHAR(25)"
		};
		schema["data"] = {
			id: {
				"primary": true,
				"type": "VARCHAR(40)"
			},
			contents: "TEXT"
		};
		return schema;
	}

	function initUserDb(){
		NRS.logConsole("Database is open");
		NRS.database.select("data", [{
			"id": "settings"
		}], function(error, result) {
			if (result && result.length > 0) {
				NRS.logConsole("Settings already exist");
				NRS.databaseFirstStart = false;
				NRS.initUserDBSuccess();
			} else {
				NRS.logConsole("Settings not found");
				NRS.databaseFirstStart = true;
				if (NRS.legacyDatabaseWithData) {
					NRS.initUserDBWithLegacyData();
				} else {
					NRS.initUserDBSuccess();
				}
			}
		});
	}

	NRS.createDatabase = function (dbName) {
		if (!NRS.isIndexedDBSupported()) {
			NRS.logConsole("IndexedDB not supported by the rendering engine, using localStorage instead");
			NRS.initLocalStorage();
			return;
		}
		var schema = createSchema();
		NRS.assetTableKeys = ["account", "accountRS", "asset", "description", "name", "position", "decimals", "quantityQNT", "groupName"];
		NRS.pollsTableKeys = ["account", "accountRS", "poll", "description", "name", "finishHeight"];
		try {
			NRS.logConsole("Opening database " + dbName);
            NRS.database = new WebDB(dbName, schema, NRS.constants.DB_VERSION, 4, function(error, db) {
                if (!error) {
                    NRS.indexedDB = db;
                    initUserDb();
                } else {
                    NRS.logConsole("Error opening database " + error);
                    NRS.initLocalStorage();
                }
            });
            NRS.logConsole("Opening database " + NRS.database);
		} catch (e) {
			NRS.logConsole("Exception opening database " + e.message);
			NRS.initLocalStorage();
		}
	};

	/* Display connected state in Sidebar */
	NRS.checkConnected = function() {
		NRS.sendRequest("getPeers+", {
			"state": "CONNECTED"
		}, function(response) {
            var connectedIndicator = $("#connected_indicator");
            if (response.peers && response.peers.length) {
				NRS.peerConnect = true;
				connectedIndicator.addClass("connected");
                connectedIndicator.find("span").html($.t("Connected")).attr("data-i18n", "connected");
				connectedIndicator.show();
			} else {
				NRS.peerConnect = false;
				connectedIndicator.removeClass("connected");
                connectedIndicator.find("span").html($.t("Not Connected")).attr("data-i18n", "not_connected");
				connectedIndicator.show();
			}
		});
	};

	NRS.getAccountInfo = function(firstRun, callback, isAccountSwitch) {
		NRS.sendRequest("getAccount", {
			"account": NRS.account,
			"includeAssets": true,
			"includeCurrencies": true,
			"includeLessors": true,
			"includeEffectiveBalance": true
		}, function(response) {
			var previousAccountInfo = NRS.accountInfo;

			NRS.accountInfo = response;

			if (response.errorCode) {
				$("#account_balance, #account_balance_sidebar, #account_nr_assets, #account_assets_balance, #account_currencies_balance, #account_nr_currencies, #account_purchase_count, #account_pending_sale_count, #account_completed_sale_count, #account_message_count, #account_alias_count").html("0");

				if (NRS.accountInfo.errorCode == 5) {
					if (NRS.downloadingBlockchain) {
						if (NRS.newlyCreatedAccount) {
                            $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_new_account", {
                                "account_id": String(NRS.accountRS).escapeHTML(),
                                "public_key": String(NRS.publicKey).escapeHTML()
                            }) + "<br/><br/>" + $.t("status_blockchain_downloading") +
                            "<br/><br/>" + NRS.getFundAccountLink()).show();
						} else {
							$("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_blockchain_downloading")).show();
						}
					} else if (NRS.state && NRS.state.isScanning) {
						$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html($.t("status_blockchain_rescanning")).show();
					} else {
                        if (NRS.publicKey == "") {
                            $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_new_account_no_pk_v2", {
                                "account_id": String(NRS.accountRS).escapeHTML()
                            })).show();
                        } else {
                            $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_new_account", {
                                "account_id": String(NRS.accountRS).escapeHTML(),
                                "public_key": String(NRS.publicKey).escapeHTML()
                            }) + "<br/><br/>");
                        }
					}
				} else {
					$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html(NRS.accountInfo.errorDescription ? NRS.accountInfo.errorDescription.escapeHTML() : $.t("error_unknown")).show();
				}
			} else {
				if (NRS.accountRS && NRS.accountInfo.accountRS != NRS.accountRS) {
					$.growl("Generated Reed Solomon address different from the one in the blockchain!", {
						"type": "danger"
					});
					NRS.accountRS = NRS.accountInfo.accountRS;
				}

				if (NRS.downloadingBlockchain) {
					$("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_blockchain_downloading")).show();
				} else if (NRS.state && NRS.state.isScanning) {
					$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html($.t("status_blockchain_rescanning")).show();
				} else if (!NRS.accountInfo.publicKey) {
                    var warning = NRS.publicKey != 'undefined' ? $.t("public_key_not_announced_warning", { "public_key": NRS.publicKey }) : $.t("no_public_key_warning");
					$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html(warning + " " + $.t("public_key_actions")).show();
				} else {
					$("#dashboard_message").hide();
				}

				// only show if happened within last week and not during account switch
				var showAssetDifference = !isAccountSwitch &&
					((!NRS.downloadingBlockchain || (NRS.blocks && NRS.blocks[0] && NRS.state && NRS.state.time - NRS.blocks[0].timestamp < 60 * 60 * 24 * 7)));

                // When switching account this query returns error
                if (!isAccountSwitch) {
                    NRS.storageSelect("data", [{
                        "id": "asset_balances"
                    }], function (error, asset_balance) {
                        if (asset_balance && asset_balance.length) {
                            var previous_balances = asset_balance[0].contents;
                            if (!NRS.accountInfo.assetBalances) {
                                NRS.accountInfo.assetBalances = [];
                            }
                            var current_balances = JSON.stringify(NRS.accountInfo.assetBalances);
                            if (previous_balances != current_balances) {
                                if (previous_balances != "undefined" && typeof previous_balances != "undefined") {
                                    previous_balances = JSON.parse(previous_balances);
                                } else {
                                    previous_balances = [];
                                }
                                NRS.storageUpdate("data", {
                                    contents: current_balances
                                }, [{
                                    id: "asset_balances"
                                }]);
                                if (showAssetDifference) {
                                    NRS.checkAssetDifferences(NRS.accountInfo.assetBalances, previous_balances);
                                }
                            }
                        } else {
                            NRS.storageInsert("data", "id", {
                                id: "asset_balances",
                                contents: JSON.stringify(NRS.accountInfo.assetBalances)
                            });
                        }
                    });
                }

				$("#account_balance, #account_balance_sidebar").html(NRS.formatStyledAmount(response.unconfirmedBalanceNQT));
				$("#account_forged_balance").html(NRS.formatStyledAmount(response.forgedBalanceNQT));






				/* Display message count in top and limit to 100 for now because of possible performance issues*/
				NRS.sendRequest("getBlockchainTransactions+", {
					"account": NRS.account,
					"type": 1,
					"subtype": 0,
					"firstIndex": 0,
					"lastIndex": 99
				}, function(response) {
					if (response.transactions && response.transactions.length) {
						if (response.transactions.length > 99)
							$("#account_message_count").empty().append("99+");
						else
							$("#account_message_count").empty().append(response.transactions.length);
					} else {
						$("#account_message_count").empty().append("0");
					}
				});

				/***  ******************   ***/

				var para;
        var emission = 300000;


        NRS.sendRequest("getBlockchainStatus", {}, function(status){
          if (status != null && status.numberOfBlocks != null && status.numberOfBlocks > 571800) {
            NRS.sendRequest("getAccount+", {"account":"PRIZM-TE8N-B3VM-JJQH-5NYJB"}, function(response){
              if (response != null && response.balanceNQT != null) {
                var genesisAmount = response.balanceNQT;
                var MAXIMUM_PARAMINING_AMOUNT = -600000000000;
                emission = Math.abs(genesisAmount);
                var ams = emission * 100 / Math.abs(MAXIMUM_PARAMINING_AMOUNT);
                if (status.numberOfBlocks >= NRS.constants.DOUBLE_PARATAX_BLOCK)
                  ams = 2 * ams;
                if (ams > 99)
                  ams = 99;
                if (ams < 0)
                  ams = 0;
                NRS.sendRequest("getPara+", {
        					"account":NRS.account
        				}, function(response) {
        					if (response.balance != null) {
        						para = response;
                    var multi = Number(para.multiplier);
                    if (multi < 0.0012)
                        multi = multi * (86400/50);
        						$("#account_amount").empty().append(NRS.formatStyledAmount(response.amount));
        						$("#account_multiplier").empty().append(NRS.fixMultiplier(multi).toFixed(2) + "%");
        						if (NRS.paraminingCounterId) {
        							clearInterval (NRS.paraminingCounterId);
        						}
                    $("#account_hold").html(NRS.formatStyledAmount(response.hold));
        						NRS.paraminingCounterId = setInterval (function () {
        							// $("#account_paramining").html(NRS.formatNumberSmart(NRS.getGoodGrow(parseInt(para.balance), parseInt(para.last), Number(para.multiplier))));
                        $("#account_paramining").html(NRS.formatNumberSmart(new prizm.Para().calc(parseInt(para.balance), parseInt(para.amount), Number(para.last), emission)));
        						}, 100);
        					}
        				});
              }
            });
          }
        });








				NRS.updateAccountControlStatus();

				if (response.name) {
					$("#account_name").html(NRS.addEllipsis(response.name.escapeHTML(), 17)).removeAttr("data-i18n");
				} else {
					$("#account_name").html($.t("set_account_info"));
				}
			}

			if (firstRun) {
				$("#account_balance, #account_hold, #account_balance_sidebar, #account_assets_balance, #account_nr_assets, #account_currencies_balance, #account_nr_currencies, #account_purchase_count, #account_pending_sale_count, #account_completed_sale_count, #account_message_count, #account_amount, #account_multiplier").removeClass("loading_dots");
			}

			if (callback) {
				callback();
			}
		});
	};


	NRS.updateAccountControlStatus = function() {
		var onNoPhasingOnly = function() {
			$("#setup_mandatory_approval").show();
			$("#mandatory_approval_details").hide();
			delete NRS.accountInfo.phasingOnly;
		};
		if (NRS.accountInfo.accountControls && $.inArray('PHASING_ONLY', NRS.accountInfo.accountControls) > -1) {
			NRS.sendRequest("getPhasingOnlyControl", {
				"account": NRS.account
			}, function (response) {
				if (response && response.votingModel >= 0) {
					$("#setup_mandatory_approval").hide();
					$("#mandatory_approval_details").show();

					NRS.accountInfo.phasingOnly = response;
					var infoTable = $("#mandatory_approval_info_table");
					infoTable.find("tbody").empty();
					var data = {};
					var params = NRS.phasingControlObjectToPhasingParams(response);
					params.phasingWhitelist = params.phasingWhitelisted;
					NRS.getPhasingDetails(data, params);
					delete data.full_hash_formatted_html;
					if (response.minDuration) {
						data.minimum_duration_short = response.minDuration;
					}

					if (response.maxDuration) {
						data.maximum_duration_short = response.maxDuration;
					}

					if (response.maxFees) {
						data.maximum_fees = NRS.convertToPRIZM(response.maxFees);
					}

					infoTable.find("tbody").append(NRS.createInfoTable(data));
					infoTable.show();
				} else {
					onNoPhasingOnly();
				}

			});

		} else {
			onNoPhasingOnly();
		}
	};

	NRS.checkAssetDifferences = function(current_balances, previous_balances) {
		var current_balances_ = {};
		var previous_balances_ = {};

		if (previous_balances && previous_balances.length) {
			for (var k in previous_balances) {
                if (!previous_balances.hasOwnProperty(k)) {
                    continue;
                }
				previous_balances_[previous_balances[k].asset] = previous_balances[k].balanceQNT;
			}
		}

		if (current_balances && current_balances.length) {
			for (k in current_balances) {
                if (!current_balances.hasOwnProperty(k)) {
                    continue;
                }
				current_balances_[current_balances[k].asset] = current_balances[k].balanceQNT;
			}
		}

		var diff = {};

		for (k in previous_balances_) {
            if (!previous_balances_.hasOwnProperty(k)) {
                continue;
            }
			if (!(k in current_balances_)) {
				diff[k] = "-" + previous_balances_[k];
			} else if (previous_balances_[k] !== current_balances_[k]) {
                diff[k] = (new BigInteger(current_balances_[k]).subtract(new BigInteger(previous_balances_[k]))).toString();
			}
		}

		for (k in current_balances_) {
            if (!current_balances_.hasOwnProperty(k)) {
                continue;
            }
			if (!(k in previous_balances_)) {
				diff[k] = current_balances_[k]; // property is new
			}
		}

		var nr = Object.keys(diff).length;
		if (nr == 0) {
        } else if (nr <= 3) {
			for (k in diff) {
                if (!diff.hasOwnProperty(k)) {
                    continue;
                }
				NRS.sendRequest("getAsset", {
					"asset": k,
					"_extra": {
						"asset": k,
						"difference": diff[k]
					}
				}, function(asset, input) {
					if (asset.errorCode) {
						return;
					}
					asset.difference = input["_extra"].difference;
					asset.asset = input["_extra"].asset;
                    var quantity;
					if (asset.difference.charAt(0) != "-") {
						quantity = NRS.formatQuantity(asset.difference, asset.decimals);

						if (quantity != "0") {
							if (parseInt(quantity) == 1) {
								$.growl($.t("you_received_assets", {
									"name": String(asset.name).escapeHTML()
								}), {
									"type": "success"
								});
							} else {
								$.growl($.t("you_received_assets_plural", {
									"name": String(asset.name).escapeHTML(),
									"count": quantity
								}), {
									"type": "success"
								});
							}
							NRS.loadAssetExchangeSidebar();
						}
					} else {
						asset.difference = asset.difference.substring(1);
						quantity = NRS.formatQuantity(asset.difference, asset.decimals);
						if (quantity != "0") {
							if (parseInt(quantity) == 1) {
								$.growl($.t("you_sold_assets", {
									"name": String(asset.name).escapeHTML()
								}), {
									"type": "success"
								});
							} else {
								$.growl($.t("you_sold_assets_plural", {
									"name": String(asset.name).escapeHTML(),
									"count": quantity
								}), {
									"type": "success"
								});
							}
							NRS.loadAssetExchangeSidebar();
						}
					}
				});
			}
		} else {
			$.growl($.t("multiple_assets_differences"), {
				"type": "success"
			});
		}
	};

	NRS.updateBlockchainDownloadProgress = function() {
		var lastNumBlocks = 5000;
    var downloadingBlockchain = $('#downloading_blockchain');
    downloadingBlockchain.find('.last_num_blocks').html($.t('last_num_blocks', { "blocks": lastNumBlocks }));
    downloadingBlockchain.show();
		if (!NRS.serverConnect || !NRS.peerConnect) {
			downloadingBlockchain.find(".db_active").hide();
			downloadingBlockchain.find(".db_halted").show();
		} else {
			downloadingBlockchain.find(".db_halted").hide();
			downloadingBlockchain.find(".db_active").show();

			var percentageTotal = 0;
			var blocksLeft;
			var percentageLast = 0;
			if (NRS.state.lastBlockchainFeederHeight && NRS.state.numberOfBlocks <= NRS.state.lastBlockchainFeederHeight) {
				percentageTotal = parseInt(Math.round((NRS.state.numberOfBlocks / NRS.state.lastBlockchainFeederHeight) * 100), 10);
				blocksLeft = NRS.state.lastBlockchainFeederHeight - NRS.state.numberOfBlocks;
				if (blocksLeft <= lastNumBlocks && NRS.state.lastBlockchainFeederHeight > lastNumBlocks) {
					percentageLast = parseInt(Math.round(((lastNumBlocks - blocksLeft) / lastNumBlocks) * 100), 10);
				}
			}
			if (!blocksLeft || blocksLeft < parseInt(lastNumBlocks / 2)) {
				downloadingBlockchain.find(".db_progress_total").hide();
			} else {
				downloadingBlockchain.find(".db_progress_total").show();
				downloadingBlockchain.find(".db_progress_total .progress-bar").css("width", percentageTotal + "%");
				downloadingBlockchain.find(".db_progress_total .sr-only").html($.t("percent_complete", {
					"percent": percentageTotal
				}));
			}
			if (!blocksLeft || blocksLeft >= (lastNumBlocks * 2) || NRS.state.lastBlockchainFeederHeight <= lastNumBlocks) {
				downloadingBlockchain.find(".db_progress_last").hide();
			} else {
				downloadingBlockchain.find(".db_progress_last").show();
				downloadingBlockchain.find(".db_progress_last .progress-bar").css("width", percentageLast + "%");
				downloadingBlockchain.find(".db_progress_last .sr-only").html($.t("percent_complete", {
					"percent": percentageLast
				}));
			}
			if (blocksLeft) {
				downloadingBlockchain.find(".blocks_left_outer").show();
				downloadingBlockchain.find(".blocks_left").html($.t("blocks_left", { "numBlocks": blocksLeft }));
			}
		}
	};

	NRS.checkIfOnAFork = function() {
		if (!NRS.downloadingBlockchain) {
			var isForgingAllBlocks = true;
			if (NRS.blocks && NRS.blocks.length >= 10) {
				for (var i = 0; i < 10; i++) {
					if (NRS.blocks[i].generator != NRS.account) {
						isForgingAllBlocks = false;
						break;
					}
				}
			} else {
				isForgingAllBlocks = false;
			}

			if (isForgingAllBlocks) {
				$.growl($.t("fork_warning"), {
					"type": "danger"
				});
			}
			var isBaseTargetTooHigh = NRS.baseTargetPercent(NRS.blocks[0]) > 1000 && !NRS.isTestNet;
            if (isBaseTargetTooHigh) {
                $.growl($.t("fork_warning_base_target"), {
                    "type": "danger"
                });
            }
			NRS.isOnFork = isForgingAllBlocks || isBaseTargetTooHigh;
			if (NRS.isOnFork) { // Disable transactions, account info and messaging
					NRS.disableOperations();
			} else { // Enable disabled things
					NRS.enableOperations();
			}
		}
	};

NRS.disableOperations = function() {
	var sendMoneyModal = $('#send_money_modal');
	var sendMoneyModalLocked = $('#send_money_modal_locked');
	if (sendMoneyModal.length && sendMoneyModalLocked.length) {
		sendMoneyModal.attr("id", "send_money_modal_disabled");
		sendMoneyModalLocked.attr("id", "send_money_modal");
	}
	var sendMessageModal = $('#send_message_modal');
	var sendMessageModalLocked = $('#send_message_modal_locked');
	if (sendMessageModal.length && sendMessageModalLocked.length) {
		sendMessageModal.attr("id", "send_message_modal_disabled");
		sendMessageModalLocked.attr("id", "send_message_modal");
	}
	var accountInfoModal = $('#account_info_modal');
	var accountInfoModalLocked = $('#account_info_modal_locked');
	if (accountInfoModal.length && accountInfoModalLocked.length) {
		accountInfoModal.attr("id", "account_info_modal_disabled");
		accountInfoModalLocked.attr("id", "account_info_modal");
	}
}

NRS.enableOperations = function () {
	var sendMoneyModal = $('#send_money_modal_disabled');
	var sendMoneyModalLocked = $('#send_money_modal_locked');
	if (sendMoneyModal.length && !sendMoneyModalLocked.length) {
		$('#send_money_modal').attr("id", "send_money_modal_locked");
		sendMoneyModal.attr("id", "send_money_modal");
	}
	var sendMessageModal = $('#send_message_modal_disabled');
	var sendMessageModalLocked = $('#send_message_modal_locked');
	if (sendMessageModal.length && !sendMessageModalLocked.length) {
		$('#send_message_modal').attr("id", "send_message_modal_locked");
		sendMessageModal.attr("id", "send_message_modal");
	}
	var accountInfoModal = $('#account_info_modal_disabled');
	var accountInfoModalLocked = $('#account_info_modal_locked');
	if (accountInfoModal.length && !accountInfoModalLocked.length) {
		$('#account_info_modal').attr("id", "account_info_modal_locked");
		accountInfoModal.attr("id", "account_info_modal");
	}
}
    NRS.printEnvInfo = function() {
        NRS.logProperty("navigator.userAgent");
        NRS.logProperty("navigator.platform");
        NRS.logProperty("navigator.appVersion");
        NRS.logProperty("navigator.appName");
        NRS.logProperty("navigator.appCodeName");
        NRS.logProperty("navigator.hardwareConcurrency");
        NRS.logProperty("navigator.maxTouchPoints");
        NRS.logProperty("navigator.languages");
        NRS.logProperty("navigator.language");
        NRS.logProperty("navigator.userLanguage");
        NRS.logProperty("navigator.cookieEnabled");
        NRS.logProperty("navigator.onLine");
        NRS.logProperty("NRS.isTestNet");
        NRS.logProperty("NRS.needsAdminPassword");
    };

	$("#id_search").on("submit", function(e) {
		e.preventDefault();

		var id = $.trim($("#id_search").find("input[name=q]").val());

		if (/PRIZM\-/i.test(id)) {
			NRS.sendRequest("getAccount", {
				"account": id
			}, function(response, input) {
				if (!response.errorCode) {
					response.account = input.account;
					NRS.showAccountModal(response);
				} else {
					$.growl($.t("error_search_no_results"), {
						"type": "danger"
					});
				}
			});
		} else {
			if (!/^\d+$/.test(id)) {
				$.growl($.t("error_search_invalid"), {
					"type": "danger"
				});
				return;
			}
			NRS.sendRequest("getTransaction", {
				"transaction": id
			}, function(response, input) {
				if (!response.errorCode) {
					response.transaction = input.transaction;
					NRS.showTransactionModal(response);
				} else {
					NRS.sendRequest("getAccount", {
						"account": id
					}, function(response, input) {
						if (!response.errorCode) {
							response.account = input.account;
							NRS.showAccountModal(response);
						} else {
							NRS.sendRequest("getBlock", {
								"block": id,
                                "includeTransactions": "true"
							}, function(response, input) {
								if (!response.errorCode) {
									response.block = input.block;
									NRS.showBlockModal(response);
								} else {
									$.growl($.t("error_search_no_results"), {
										"type": "danger"
									});
								}
							});
						}
					});
				}
			});
		}
	});

	function checkLocalStorage() {
	    var storage;
	    var fail;
	    var uid;
	    try {
	        uid = String(new Date());
	        (storage = window.localStorage).setItem(uid, uid);
	        fail = storage.getItem(uid) != uid;
	        storage.removeItem(uid);
	        fail && (storage = false);
	    } catch (exception) {
	        NRS.logConsole("checkLocalStorage " + exception.message)
	    }
	    return storage;
	}

	return NRS;
}(NRS || {}, jQuery));

$(document).ready(function() {
	console.log("document.ready");
	NRS.init();
  var hours = new Date().getHours();
  var s = $("#darkprizm");
  console.log("Autoswitching theme");
  if (hours < 8 || hours > 20) {
    console.log("Enabled night theme");
    $('head').append('<link rel="stylesheet" type="text/css" id="darkprizm" href="css/darkprizm.css">');
  } else {
    if (s.length > 0) {
      console.log("Enabled day theme");
      s.remove();
    }
  }
});
