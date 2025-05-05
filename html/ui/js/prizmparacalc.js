var prizm;
(function (prizm) {
    var Para = (function () {
        function Para() {
        }
        Para.prototype.calc = function (balance, amount, last, genesisBalance) {
            var payout1 = this.ordinary(balance, amount, last, genesisBalance);
            var payout2 = this.compound(balance, amount, last, genesisBalance);
            var payout = payout1 > payout2 ? payout2 : payout1;
            var emission = Math.abs(genesisBalance);
            var mtx = emission / 6.0E11;
            if (mtx > 1.0)
                mtx = 1.0;
            mtx = 1.0 - mtx;
            if (mtx < 0.1)
                mtx = 0.1;
            payout *= mtx;
            if (payout > 1.0E8)
                payout = 100000000;
            if (payout > 0 && payout + balance > 1.0E8) {
                payout = 1.0E8 - balance;
            }
            return payout / 100.0;
        };
        Para.prototype.ordinary = function (balance_in, amount_in, last, genesisBalance) {
            var balance = balance_in;
            var amount = amount_in;
            var multi = this.multi(balance, amount) / 100.0;
            var days = this.days(last);
            var payout = balance * (days * multi);
            var paraTax = this.parataxPercent(genesisBalance);
            if (paraTax > 0) {
                var paraTaxAmount = this.getPercentAmount(payout, paraTax);
                payout = this.getAmountMinusPercent(payout, paraTax);
            }
            return payout;
        };
        Para.prototype.compound = function (balance_in, amount_in, last, genesisBalance) {
            var balance = balance_in;
            var amount = amount_in;
            var multi = (this.multi(balance, amount) / 100.0) / 1728.0;
            var periods = this.periods(last);
            var paraTax = this.doubleParataxPercent(genesisBalance);
            var payout = (balance * Math.pow(1.0 + multi, periods)) - balance;
            if (paraTax > 0) {
                var paraTaxAmount = this.getPercentAmount(payout, paraTax);
                payout = this.getAmountMinusPercent(payout, paraTax);
            }
            if (payout < 0)
                return 0;
            return payout;
        };
        Para.prototype.days = function (last) {
            var seconds = this.seconds(last);
            return seconds / 86400.0;
        };
        Para.prototype.seconds = function (last) {
            var time = Date.now();
            time = time / 1000.0;
            var diff = last;
            diff = diff + 1.53271548E9;
            return time - diff;
        };
        Para.prototype.periods = function (last) {
            return this.seconds(last) / 50.0;
        };
        Para.prototype.multi = function (balance, amount) {
            var multi = 1.0;
            var percent = 0.0;
            if (balance >= 100 && balance <= 9999)
                percent = 0.12;
            if (balance >= 10000 && balance <= 99999)
                percent = 0.14;
            if (balance >= 100000 && balance <= 999999)
                percent = 0.18;
            if (balance >= 1000000 && balance <= 4999999)
                percent = 0.21;
            if (balance >= 5000000 && balance <= 9999999)
                percent = 0.25;
            if (balance >= 10000000 && balance <= 49999999)
                percent = 0.28;
            if (balance >= 50000000 && balance < 100000000)
                percent = 0.33;
            if (amount >= 100000 && amount <= 999999)
                multi = 2.18;
            if (amount >= 1000000 && amount <= 9999999)
                multi = 2.36;
            if (amount >= 10000000 && amount <= 99999999)
                multi = 2.77;
            if (amount >= 100000000 && amount <= 999999999)
                multi = 3.05;
            if (amount >= 1000000000 && amount <= 9999999999)
                multi = 3.36;
            if (amount >= 10000000000 && amount <= 99999999999)
                multi = 3.88;
            if (amount >= 100000000000)
                multi = 4.37;
            return multi * percent;
        };
        Para.prototype.doubleParataxPercent = function (genesisBalance) {
            var percent = this.parataxPercent(genesisBalance);
            percent = 2 * percent;
            return this.clampParataxPercent(percent);
        };
        Para.prototype.parataxPercent = function (genesisBalance) {
            var emission = Math.abs(genesisBalance);
            var ams = (function (n) { return n < 0 ? Math.ceil(n) : Math.floor(n); })((emission * 100) / 600000000000);
            return this.clampParataxPercent(ams);
        };
        Para.prototype.clampParataxPercent = function (percent) {
            if (percent > 98)
                return 98;
            if (percent < 0)
                return 0;
            return percent;
        };
        Para.prototype.getPercentAmount = function (amount, percent) {
            return (amount * percent) / 100.0;
        };
        Para.prototype.getAmountMinusPercent = function (amount, percent) {
            return amount - this.getPercentAmount(amount, percent);
        };
        return Para;
    }());
    prizm.Para = Para;
    Para["__class"] = "prizm.Para";
})(prizm || (prizm = {}));

