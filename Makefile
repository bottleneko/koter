.DEFAULT_GOAL := all

.NOTPARALLEL:

MAVEN := mvn
TCPDUMP := sudo tcpdump
ERL := erl

.PHONY: all
all: compile run

.PHONY: compile
compile:
	$(MAVEN) compile

.PHONY: run
run:
	$(MAVEN) exec:java

.PHONY: tcpdump
tcpdump:
	$(TCPDUMP) -i lo -s0 -v port 4369

.PHONY: empd
epmd:
	$(ERL) -name test@localhost
