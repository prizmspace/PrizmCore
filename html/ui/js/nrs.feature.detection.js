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
var NRS = (function (NRS) {

    var isDesktopApplication = navigator.userAgent.indexOf("JavaFX") >= 0;

    NRS.isIndexedDBSupported = function() {
        return window.indexedDB !== undefined;
    };

    NRS.isCoinExchangePageAvailable = function() {
        return !isDesktopApplication; // JavaFX does not support CORS required by ShapeShift
    };

    NRS.isExternalLinkVisible = function() {
        // When using JavaFX add a link to a web wallet except on Linux since on Ubuntu it sometimes hangs
        return isDesktopApplication && navigator.userAgent.indexOf("Linux") == -1;
    };

    NRS.isDiscoveryAvailableToLaunch = function () {
      return isDesktopApplication;
    }
    NRS.DISCOVERY_PUBLIC = 1;
    NRS.DISCOVERY_PROTECTED = 2;
    NRS.DISCOVERY_PRIVATE = 3;
    NRS.DISCOVERY_UNAVAILABLE = 0;
    NRS.getDiscoveryState = function (callback) {
      NRS.sendRequest("getParent", {"account":"PRIZM-CUQW-4FP2-DGYZ-DFYKL"}, function(response){
        if (response.errorDescription) {
          if (NRS.compareStrings(response.errorDescription, 'Incorrect "adminPassword" (the specified password does not match prizm.adminPassword)')
              || NRS.compareStrings(response.errorDescription, 'Administrator password not specified.')) {
            callback(NRS.DISCOVERY_PROTECTED);
            return;
          }
          if (NRS.compareStrings(response.errorDescription, 'Not allowed')) {
            callback(NRS.DISCOVERY_PRIVATE);
            return;
          }
          callback(NRS.DISCOVERY_UNAVAILABLE);
        } else {
          if (response.parentRS.lastIndexOf("PRIZM-", 0) === 0 || NRS.compareStrings(response.errorDescription, 'Incorrect "account"')) {
            callback(NRS.DISCOVERY_PUBLIC);
            return;
          }
          callback(NRS.DISCOVERY_UNAVAILABLE);
        }
      });
    }

    NRS.compareStrings = function (string1, string2) {
      var areEqual = string1.trim().valueOf() === string2.trim().valueOf();
      // console.log(string1 + ' equals ' + string2 + '? ' + areEqual);
      return areEqual;
    }

    NRS.isPollGetState = function() {
        return !isDesktopApplication; // When using JavaFX do not poll the server
    };

    NRS.isExportContactsAvailable = function() {
        return !isDesktopApplication; // When using JavaFX you cannot export the contact list
    };

    NRS.isShowDummyCheckbox = function() {
        return isDesktopApplication && navigator.userAgent.indexOf("Linux") >= 0; // Correct rendering problem of checkboxes on Linux
    };

    return NRS;
}(NRS || {}, jQuery));
