QUnit.module("prizm.address");

QUnit.test("prizmAddress", function (assert) {
    var address = new PrizmAddress();
    assert.equal(address.set("prizm----"), true, "valid address");
    assert.equal(address.toString(), "PRIZM----", "address");
    assert.equal(address.set("PRIZM----"), false, "invalid address");
});
