{
  description = "Development environment for micro-launcher";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        buildToolsVersion = "35.0.0";
        androidSdk = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [
            "35"
            "36"
          ];
          buildToolsVersions = [ buildToolsVersion ];
          abiVersions = [
            "arm64-v8a"
          ];
          includeNDK = true;
        };

        sdkAapt2 = "${androidSdk.androidsdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2";

        aapt2Wrapped =
          if pkgs.stdenv.hostPlatform.isAarch64 && pkgs.stdenv.hostPlatform.isLinux then
            pkgs.writeShellScriptBin "aapt2" ''
              exec ${pkgs.qemu-user}/bin/qemu-x86_64 \
                -L ${pkgs.pkgsCross.gnu64.glibc} \
                -E LD_LIBRARY_PATH=${pkgs.pkgsCross.gnu64.gcc.cc.lib}/lib:${pkgs.pkgsCross.gnu64.glibc}/lib \
                ${sdkAapt2} "$@"
            ''
          else
            pkgs.writeShellScriptBin "aapt2" ''
              exec ${sdkAapt2} "$@"
            '';
      in
      {
        devShells.default = pkgs.mkShell {
          ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
          ANDROID_NDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk/ndk-bundle";
          JAVA_HOME = "${pkgs.jdk21_headless}";

          buildInputs = with pkgs; [
            androidSdk.androidsdk
            jdk21_headless
            git
          ];

          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2Wrapped}/bin/aapt2";

          shellHook = ''
            export PATH="${pkgs.jdk21_headless}/bin:${aapt2Wrapped}/bin:$PATH"
          '';
        };
      }
    );
}



