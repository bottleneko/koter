.DEFAULT_GOAL := all

.NOTPARALLEL:

MAVEN := mvn

.PHONY: all
all: compile run

.PHONY: compile
compile:
	$(MAVEN) compile

.PHONY: run
run:
	$(MAVEN) exec:java
