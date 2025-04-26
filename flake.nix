{
  description = "Java Code Query with AST, Spring AI, and Semantic Search";

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
        pkgs = nixpkgs.legacyPackages.${system};
        javaVersion = 17;

        # JDK for development
        jdk = pkgs.jdk17;

        # Gradle with Java 17
        gradle = pkgs.gradle.override {
          java = jdk;
        };
      in
      {
        devShells.default = pkgs.mkShell {
          name = "javanalyser";
          buildInputs = [
            jdk
            gradle
            pkgs.postgresql
            pkgs.spring-boot-cli
          ];

          shellHook = ''
            export JAVA_HOME=${jdk}/lib/openjdk
            export GRADLE_USER_HOME=$PWD/.gradle
            echo "Java Code Query Development Environment"
            echo "JDK: $(java -version 2>&1 | head -n 1)"
            echo "Gradle: $(gradle --version | head -n 3 | tail -n 1)"
            echo ""
            echo "Available commands:"
            echo "  gradle bootRun    - Run the application"
            echo "  gradle build      - Build the application"
            echo "  gradle test       - Run tests"
            echo ""
            echo "You may need to start Ollama separately with:"
            echo "  ollama serve &"
            echo "And pull the codellama model:"
            echo "  ollama pull codellama"
          '';
        };
      }
    );
}
