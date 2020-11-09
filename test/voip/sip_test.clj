(ns voip.sip-test
  (:require  [clojure.test :as t :refer [deftest is testing]]
             [voip.core.sip :as SUT]))

(def sip-invite "INVITE sip:alan@jasomi.com
TO : alan@jasomi.com
From: ralph@example.com
MaX-fOrWaRdS: 0068
Call-ID: test.0ha0isndaksdj@192.0.2.1
Xyzzy-2: this is the number ten : 10
Xyzzy-3: INVITE
Xyzzy: 10000000000
Meaning: foo bar spam
Foobar roobar
Content-Length: 18
Content-Type: application/sdp

v=0
testing=123")

(deftest parse-invite-hdr-test []
  (testing "SIP INVITE header"
    (is (= {:invite {:to "alan@jasomi.com"}} (SUT/parse-header sip-invite)))))

