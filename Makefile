.PHONY: all debug release clean help
all: debug release
debug:
	nix develop --command ./gradlew assembleDebug
release:
	nix develop --command ./gradlew assembleRelease
clean:
	nix develop --command ./gradlew clean
