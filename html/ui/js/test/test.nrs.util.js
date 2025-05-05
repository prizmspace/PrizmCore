QUnit.module("nrs.util");

QUnit.test("convertToPRIZM", function (assert) {
    assert.equal(NRS.convertToPRIZM(200), "2", "whole");
    assert.equal(NRS.convertToPRIZM(20), "0.2", "fraction");
    assert.equal(NRS.convertToPRIZM(-200), "-2", "negative");
    assert.equal(NRS.convertToPRIZM(-20), "-0.2", "fraction.negative");
    assert.equal(NRS.convertToPRIZM(-220), "-2.2", "whole.fraction.negative");
    assert.equal(NRS.convertToPRIZM(2), "0.02", "nqt");
    assert.equal(NRS.convertToPRIZM(-2), "-0.02", "nqt.negative");
    assert.equal(NRS.convertToPRIZM(new BigInteger(String(2))), "0.02", "input.object");
    assert.equal(NRS.convertToPRIZM("hi"), "1.88", "alphanumeric"); // strange behavior of BigInteger don't do that
    assert.throws(function () {
        NRS.convertToPRIZM(null);
    }, {
        "message": "Cannot read property 'compareTo' of null",
        "name": "TypeError"
    }, "null.value");
});

QUnit.test("format", function (assert) {
    assert.equal(NRS.format("12345"), Number("12345").toLocaleString(), "escaped");
    assert.equal(NRS.format("12345", true), Number(12345).toLocaleString(), "not.escaped");
    assert.equal(NRS.format("-12345", false), Number(-12345).toLocaleString(), "neg");
    assert.equal(NRS.format("-12345", true), Number("-12345").toLocaleString(), "neg.not.escaped");
    assert.equal(NRS.format("-12345.67", true), Number("-12345.67").toLocaleString(), "decimal.not.good"); // bug ?
    assert.equal(NRS.format({ amount: 1234, negative: '-', mantissa: ".567"}, true), Number(-1234.567).toLocaleString(), "object");
    assert.equal(NRS.format("12.34", false, 4), "12.34", "zero.pad");
    assert.equal(NRS.format("12", false, 4), "12.00", "zero.pad.whole");
    assert.equal(NRS.format("12.", false, 4), "12.00", "zero.pad.whole");
    assert.equal(NRS.format("12.34567", false, 4), "12.34", "zero.pad.not.necessary");
    assert.equal(NRS.format("12", false, 0), "12", "zero.to.pad");
});

QUnit.test("formatAmount", function (assert) {
    assert.equal(NRS.formatAmount("12345", false, false), "123.45", "nqt");
    assert.equal(NRS.formatAmount("12345", true, false), "123.45", "nqt.rounding");
    assert.equal(NRS.formatAmount("123450", false, false), "1234.5", "string");
    assert.equal(NRS.formatAmount("123450", true, false), "1234.5", "string.no.rounding");
    assert.equal(NRS.formatAmount(123.45, false, false), "123.45", "number");
    assert.equal(NRS.formatAmount(123.455, true, false), "123.46", "number.rounding");
    assert.equal(NRS.formatAmount(12.343, true, false), "123.34", "number.rounding");
    assert.equal(NRS.formatAmount("12345670", false, true), Number("123456.7").toLocaleString(), "1000separator");
    assert.equal(NRS.formatAmount("123456700", true, true), Number("1234567").toLocaleString(), "prizm.rounding");
    assert.equal(NRS.formatAmount("123456780", true, false), Number("1234567.8").toLocaleString(), "thousands.separator.escaped");
});

QUnit.test("formatVolume", function (assert) {
    assert.equal(NRS.formatVolume(1), "1 B", "byte");
    assert.equal(NRS.formatVolume(1000), "1'000 B", "thousand");
    assert.equal(NRS.formatVolume(1024), "1 KB", "kilo");
    assert.equal(NRS.formatVolume(1000000), "977 KB", "million");
    assert.equal(NRS.formatVolume(1024*1024), "1 MB", "million");
    assert.equal(NRS.formatVolume(2*1024*1024 + 3*1024 + 4), "2 MB", "combination");
});

QUnit.test("formatWeight", function (assert) {
    assert.equal(NRS.formatWeight(1), "1", "byte");
    assert.equal(NRS.formatWeight(1000), "1&#39;000", "thousand");
    assert.equal(NRS.formatWeight(12345), "12&#39;345", "number");
});

QUnit.test("calculateOrderPricePerWholeQNT", function (assert) {
    assert.equal(NRS.calculateOrderPricePerWholeQNT(100, 0), "1", "no.decimals.one");
    assert.equal(NRS.calculateOrderPricePerWholeQNT(1, 2), "0.01", "fraction");
    assert.equal(NRS.calculateOrderPricePerWholeQNT(-123400, 2), "-12340000", "two.decimals");
    assert.equal(NRS.calculateOrderPricePerWholeQNT(-123400, 0), "-1234", "no.decimals");
});

QUnit.test("formatOrderPricePerWholeQNT", function (assert) {
    assert.equal(NRS.formatOrderPricePerWholeQNT(100, 0), "1", "no.decimals.one");
    assert.equal(NRS.formatOrderPricePerWholeQNT(1, 2), "0.01", "fraction");
    assert.equal(NRS.formatOrderPricePerWholeQNT(-12340000, 2), Number("-123400".escapeHTML()).toLocaleString(), "four.decimals");
    assert.equal(NRS.formatOrderPricePerWholeQNT(-123400, 0), Number("-1234".escapeHTML()).toLocaleString(), "no.decimals");
});

QUnit.test("calculatePricePerWholeQNT", function (assert) {
    assert.equal(NRS.calculatePricePerWholeQNT(100, 0), "100", "no.decimals.one");
    assert.equal(NRS.calculatePricePerWholeQNT(1000000, 2), "10000", "two.decimals")
    assert.equal(NRS.calculatePricePerWholeQNT(-1234000000, 2), "-12340000".escapeHTML(), "four.decimals");
    assert.equal(NRS.calculatePricePerWholeQNT(-123400000000, 0), "-123400000000".escapeHTML(), "no.decimals");
    assert.throws(function () {
        NRS.calculatePricePerWholeQNT(101, 2);
    }, "Invalid input.", "invalid.input");
});

QUnit.test("calculateOrderTotalNQT", function (assert) {
    assert.equal(NRS.calculateOrderTotalNQT(9, 5 ), "45", "multiplication");
});

QUnit.test("calculateOrderTotal", function (assert) {
    assert.equal(NRS.calculateOrderTotal(9, 5), "0.56", "multiplication");
});

QUnit.test("calculatePercentage", function (assert) {
    assert.equal(NRS.calculatePercentage(6, 15), "40.00", "pct1");
    assert.equal(NRS.calculatePercentage(5, 15), "33.33", "pct1");
    assert.equal(NRS.calculatePercentage(10, 15), "66.67", "pct3");
    assert.equal(NRS.calculatePercentage(10, 15, 0), "66.66", "pct3.round0");
    assert.equal(NRS.calculatePercentage(10, 15, 1), "66.67", "pct3.round1");
    assert.equal(NRS.calculatePercentage(10, 15, 2), "66.67", "pct3.round2");
    assert.equal(NRS.calculatePercentage(10, 15, 3), "66.67", "pct3.round3");
});

QUnit.test("amountToPrecision", function (assert) {
    assert.equal(NRS.amountToPrecision(12, 0), "12", "multiplication");
    assert.equal(NRS.amountToPrecision(12., 0), "12", "multiplication");
    assert.equal(NRS.amountToPrecision(12.0, 0), "12", "multiplication");
    assert.equal(NRS.amountToPrecision(12.3400, 2), "12.34", "multiplication");
    assert.equal(NRS.amountToPrecision(12.3456, 2), "12.34", "multiplication");
    assert.equal(NRS.amountToPrecision(12.34, 2), "12.34", "multiplication");
    assert.equal(NRS.amountToPrecision(12.3456, 2), "12.34", "multiplication");
    assert.equal(NRS.amountToPrecision(12.3006, 2), "12.30", "multiplication");
});

QUnit.test("convertToNQT", function (assert) {
    assert.equal(NRS.convertToNQT(1), "100", "one");
    assert.equal(NRS.convertToNQT(1.), "100", "one.dot");
    assert.equal(NRS.convertToNQT(1.0), "100", "one.dot.zero");
    assert.equal(NRS.convertToNQT(.1), "100", "dot.one");
    assert.equal(NRS.convertToNQT(0.1), "100", "zero.dot.one");
    assert.equal(NRS.convertToNQT("0.01"), "1", "nqt");
    assert.throws(function () {
        NRS.convertToNQT(0.01); // since it's passed as 1e-8
    }, "Invalid input.", "invalid.input");
});

QUnit.test("convertToQNTf", function (assert) {
    assert.equal(NRS.convertToQNTf(1, 0), "1", "one");
    assert.equal(NRS.convertToQNTf(1, 2), "0.01", "milli");
    assert.equal(NRS.convertToQNTf(100, 2), "1", "three.decimals");
    assert.equal(NRS.convertToQNTf(1234567, 2), "12345.67", "multi");
    assert.deepEqual(NRS.convertToQNTf(1234567, 2, true), { amount: "12345", mantissa: ".67" }, "object");
});

QUnit.test("convertToQNT", function (assert) {
    assert.equal(NRS.convertToQNT(1, 0), "1", "one");
    assert.equal(NRS.convertToQNT(1, 3), "1000", "thousand");
    assert.equal(NRS.convertToQNT(1000, 3), "1000000", "million");
    assert.equal(NRS.convertToQNT(1.23, 2), "123", "multi");
    assert.equal(NRS.convertToQNT(0.12, 2), "12", "decimal");
    assert.throws(function() { NRS.convertToQNT(0.123, 2) }, "Fraction can only have 2 decimals max.", "too.many.decimals");
});

QUnit.test("formatQuantity", function (assert) {
    assert.equal(NRS.formatQuantity(1, 0), "1", "one");
    assert.equal(NRS.formatQuantity(10000000, 2, true), Number("100000").toLocaleString(), "thousand");
    assert.equal(NRS.formatQuantity(1234, 2, true), Number("12.34").toLocaleString(), "thousand");
    assert.equal(NRS.formatQuantity(123456, 2, true), Number("1234.56").toLocaleString(), "thousand");
    assert.equal(NRS.formatQuantity(1234567, 2, true), Number("12345.67").toLocaleString(), "thousand");
});

QUnit.test("formatAmount", function (assert) {
    assert.equal(NRS.formatAmount(1), "1", "one");
    assert.equal(NRS.formatAmount(10000000, false, true), Number("10000000").toLocaleString(), "million");
    assert.equal(NRS.formatAmount(12.34, true), Number("12.34").toLocaleString(), "thousand");
    assert.equal(NRS.formatAmount(12.345, true), Number("12.35").toLocaleString(), "thousand");
});

QUnit.test("formatTimestamp", function (assert) {
    var date = new Date(0);
    assert.equal(NRS.formatTimestamp(0, true, true), date.toLocaleDateString(), "start.date");
});

QUnit.test("getAccountLink", function (assert) {
    NRS.contacts = {};

    assert.equal(NRS.getAccountLink({}, "dummy"), "/", "non.existing");
    assert.equal(NRS.getAccountLink({ entity: 5873880488492319831 }, "entity"), "<a href='#' data-user='PRIZM-XKA2-7VJU-VZSY-7R335' class='show_account_modal_action user-info'>/</a>", "numeric");
    assert.equal(NRS.getAccountLink({ entityRS: "PRIZM-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='PRIZM-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>PRIZM-XK4R-7VJU-6EQG-7R335</a>", "RS");
    assert.equal(NRS.getAccountLink({ entity: 5873880488492319831, entityRS: "PRIZM-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='PRIZM-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>PRIZM-XK4R-7VJU-6EQG-7R335</a>", "numeric.and.RS");
    NRS.contacts = { "PRIZM-XK4R-7VJU-6EQG-7R335": { name: "foo" }};
    assert.equal(NRS.getAccountLink({ entityRS: "PRIZM-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='PRIZM-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>foo</a>", "contact");
    NRS.accountRS = "PRIZM-XK4R-7VJU-6EQG-7R335";
    assert.equal(NRS.getAccountLink({ entityRS: "PRIZM-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='PRIZM-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>You</a>", "you");
    assert.equal(NRS.getAccountLink({ entityRS: "PRIZM-XK4R-7VJU-6EQG-7R335" }, "entity", "PRIZM-XK4R-7VJU-6EQG-7R335", "My Precious"), "<a href='#' data-user='PRIZM-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>My Precious</a>", "force.account.name");
    assert.equal(NRS.getAccountLink({ entityRS: "PRIZM-XK4R-7VJU-6EQG-7R335" }, "entity", undefined, undefined, true), "<a href='#' data-user='PRIZM-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>PRIZM-XK4R-7VJU-6EQG-7R335</a>", "maintain.rs.format");
    assert.equal(NRS.getAccountLink({ entityRS: "PRIZM-XK4R-7VJU-6EQG-7R335" }, "entity", undefined, undefined, undefined, "btn btn-xs"), "<a href='#' data-user='PRIZM-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info btn btn-xs'>You</a>", "add.class");
    NRS.contacts = null;
    NRS.accountRS = null;
    NRS.constants.GENESIS = 1739068987193023818;
    NRS.constants.GENESIS_RS = "PRIZM-MR8N-2YLS-3MEQ-3CMAJ";
    assert.equal(NRS.getAccountLink({ entityRS: NRS.constants.GENESIS_RS }, "entity"), "<a href='#' data-user='PRIZM-MR8N-2YLS-3MEQ-3CMAJ' class='show_account_modal_action user-info'>Genesis</a>", "genesis");
});

QUnit.test("generateToken", function (assert) {
    NRS.constants.EPOCH_BEGINNING = 1385294400000;
    var token = NRS.generateToken("myToken", "rshw9abtpsa2");
    assert.ok(token.indexOf("e9cl0jgba7lnp7gke9rdp7hg3uvcl5cnd23") == 0);
    assert.equal(token.length, 160);
});

QUnit.test("utf8", function (assert) {
    // compare the two UTF8 conversion methods
    var str = "Hello World";
    var bytes1 = NRS.getUtf8Bytes(str);
    var bytes2 = NRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
    // Hebrew
    str = "אבג";
    bytes1 = NRS.getUtf8Bytes(str);
    bytes2 = NRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
    // Chinese Simplified
    str = "简体中文网页";
    bytes1 = NRS.getUtf8Bytes(str);
    bytes2 = NRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
    // Chinese Traditional
    str = "繁體中文網頁";
    bytes1 = NRS.getUtf8Bytes(str);
    bytes2 = NRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
});

QUnit.test("versionCompare", function (assert) {
    assert.equal(NRS.versionCompare("1.6.4", "1.7.5"), "-1", "after");
    assert.equal(NRS.versionCompare("1.7.5", "1.6.4"), "1", "before");
    assert.equal(NRS.versionCompare("1.6.4", "1.6.4"), "0", "same");
    assert.equal(NRS.versionCompare("1.6.4e", "1.6.5e"), "-1", "after.e");
    assert.equal(NRS.versionCompare("1.6.5e", "1.6.4e"), "1", "before.e");
    assert.equal(NRS.versionCompare("1.6.4e", "1.6.4e"), "0", "same.e");
    assert.equal(NRS.versionCompare("1.7.5", "1.8.0e"), "-1", "after.ga.vs.e");
    assert.equal(NRS.versionCompare("1.7.5e", "1.8.0"), "-1", "after.e.vs.ga");
    assert.equal(NRS.versionCompare("1.8.0e", "1.8.0"), "1", "same.e.before.ga");
});

QUnit.test("numberOfDecimals", function (assert) {
    NRS.getLocale();
    var rows = [{price: "1.23"}, {price: "1.23"}];
    assert.equal(NRS.getNumberOfDecimals(rows, "price"), 2, "no.callback");
    rows = [{price: "123"}, {price: "123"}];
    assert.equal(NRS.getNumberOfDecimals(rows, "price", function(val) {
        return NRS.formatAmount(val.price);
    }), 3, "with.callback");
});
