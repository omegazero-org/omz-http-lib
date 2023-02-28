
BINDIR := bin

# https://stackoverflow.com/a/18258352
rwildcard = $(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

JAVA_CP := omz-common-latest.jar:omz-netlib-common-latest.jar
JAVAC_FLAGS := -Werror -Xlint:all,-processing
JAVA_PATH_SEPARATOR := $(strip $(shell java -XshowSettings:properties 2>&1 | grep path.separator | cut -d '=' -f2))


.PHONY: all
all: core net http1 http2

.PHONY: core
core: $(BINDIR)/http-core.jar
.PHONY: net
net: $(BINDIR)/http-net.jar
.PHONY: http1
http1: $(BINDIR)/http1.jar
.PHONY: http2
http2: $(BINDIR)/http2.jar

.PHONY: clean
clean:
	rm -r $(BINDIR)/*

define pre_build
	@mkdir -p $(BINDIR)/$(1)
endef

define post_build
	@[ ! -d $(1)/main/resources ] || cp -r $(1)/main/resources/* $(BINDIR)/$(1)
	jar cf $(BINDIR)/$(1).jar -C $(BINDIR)/$(1) .
endef

$(BINDIR)/http-core.jar: $(call rwildcard,http-core/main/java,*.java)
	$(call pre_build,http-core)
	javac $(JAVAC_FLAGS) -d $(BINDIR)/http-core -cp "$(JAVA_CP)" $(filter %.java,$^)
	$(call post_build,http-core)

$(BINDIR)/http-net.jar: $(BINDIR)/http-core.jar $(call rwildcard,http-net/main/java,*.java)
	$(call pre_build,http-net)
	javac $(JAVAC_FLAGS) -d $(BINDIR)/http-net -cp "$(JAVA_CP)$(JAVA_PATH_SEPARATOR)$(BINDIR)/http-core.jar" $(filter %.java,$^)
	$(call post_build,http-net)

$(BINDIR)/http1.jar: $(BINDIR)/http-core.jar $(call rwildcard,http1/main/java,*.java)
	$(call pre_build,http1)
	javac $(JAVAC_FLAGS) -d $(BINDIR)/http1 -cp "$(JAVA_CP)$(JAVA_PATH_SEPARATOR)$(BINDIR)/http-core.jar" $(filter %.java,$^)
	$(call post_build,http1)

$(BINDIR)/http2.jar: $(BINDIR)/http-core.jar $(call rwildcard,http2/main/java,*.java)
	$(call pre_build,http2)
	javac $(JAVAC_FLAGS) -d $(BINDIR)/http1 -cp "$(JAVA_CP)$(JAVA_PATH_SEPARATOR)$(BINDIR)/http-core.jar" $(filter %.java,$^)
	$(call post_build,http2)
