package com.osatum.wbn.domain.model.journal;


/*
MARC21


a – rocznik (annual)
b – dwumiesięcznik (bimonthly)
c – dwutygodnik (semiweekly / biweekly – zależnie od kontekstu)
d – dziennik (daily)
e – tygodnik (weekly)
f – półrocznik (semiannual)
g – nieregularny (irregular)
h – trzy razy w roku (triannual)
i – trzy razy w tygodniu
j – trzy razy w miesiącu
k – ciągły (continuously updated, np. serwisy online)
m – miesięcznik (monthly)
q – kwartalnik (quarterly)
s – półmiesięcznik (semimonthly)
t – trzy razy w roku (czasem używane zamiennie z „h”)
w – tygodnik (weekly – alternatywne oznaczenie)
z – inne / nieznane

JOURNAL_FREQUENCY.24=Dwutygodnik
JOURNAL_FREQUENCY.6=Dwumiesięcznik
JOURNAL_FREQUENCY.2=Półrocznik
JOURNAL_FREQUENCY.100=Nieregularny
JOURNAL_FREQUENCY.48=Tygodnik
JOURNAL_FREQUENCY.4=Kwartalnik
JOURNAL_FREQUENCY.12=Miesięcznik
JOURNAL_FREQUENCY.1=Rocznik
JOURNAL_FREQUENCY.101=Ciągła (online)
JOURNAL_FREQUENCY.3=3 wydania w roku
 */
public enum JournalFrequency {

    /* dwutygodnik (semiweekly / biweekly – zależnie od kontekstu) */
    c(24),
    /* dwumiesięcznik (bimonthly) */
    b(6),
    /* półrocznik (semiannual) */
    f(2),
    /* g – nieregularny (irregular) */
    g(100),
    /* e – tygodnik (weekly) */
    e(48) ,
    /* q – kwartalnik (quarterly) */
    q(4),
    /* m – miesięcznik (monthly) */
    m(12),
    /* a – rocznik (annual) */
    a(1),
    /* k – ciągły (continuously updated, np. serwisy online) */
    k(101),
    /* t – trzy razy w roku (czasem używane zamiennie z „h”) */
    t(3);

    private Integer code;

    JournalFrequency(Integer code) {
        this.code = code;
    }
}
