{
  description = "A Nix Flake providing a development environment for Chisel";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.05";
    openxc7.url = "github:openxc7/toolchain-nix";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, openxc7, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        openxc7Pkgs = openxc7.packages.${system};

        packages = with pkgs; [
          mill
          verilator
          circt
          python3
          just
          surfer 
          gtkwave
        ];

      in {
        devShells = {
          default = pkgs.mkShell { name = "chisel"; inherit packages; };
        };
      });
}
