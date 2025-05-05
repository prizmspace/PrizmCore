QUnit.module("extensions");

QUnit.test("escapeHtml", function (assert) {
    assert.equal("&\"<>'".escapeHTML(), "&amp;&quot;&lt;&gt;&#39;", "all.escaped.chars");
    assert.equal("<script>alert('hi');</script>".escapeHTML(), "&lt;script&gt;alert(&#39;hi&#39;);&lt;/script&gt;", "xss.prevention");
});

QUnit.test("autoLink", function (assert) {
    assert.equal("<script>alert('hi');</script>".autoLink(), "&lt;script&gt;alert(&#39;hi&#39;);&lt;/script&gt;", "xss.prevention");
    
});

