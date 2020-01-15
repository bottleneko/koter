.DEFAULT_GOAL := all

.NOTPARALLEL:

COMPOSE := docker-compose

workspace_exec := $(COMPOSE) exec workspace
epmd_exec := $(COMPOSE) exec epmd

MAVEN := $(workspace_exec) mvn
TCPDUMP := sudo tcpdump
ERL := $(epmd_exec) erl

.PHONY: all
all: workspace-build workspace-up compile run

.PHONY: compile
compile:
	$(MAVEN) compile

.PHONY: run
run:
	$(MAVEN) exec:java

.PHONY: tcpdump
tcpdump:
	$(TCPDUMP) -i lo -s0 -v port 4369

### ==================================================================
### Управление локальным окружением
### ==================================================================

.PHONY: epmd
epmd: NODE_NAME ?= test@localhost
epmd: ; $(ERL) -name $(NODE_NAME)

.PHONY: workspace
workspace: ; $(workspace_exec) bash

.PHONY: workspace-build
workspace-build: ; $(COMPOSE) build

.PHONY: workspace-up
workspace-up: ; $(COMPOSE) up --detach --remove-orphans

.PHONY: workspace-down
workspace-down: ; $(COMPOSE) down --remove-orphans
