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
          gtkwave
          python3
          gnumake
          feh
          just
        ];

        synthPackages = with pkgs; [
          yosys
          ghdl
          yosys-ghdl
          openfpgaloader
          pypy310
          python312Packages.pyyaml
          python312Packages.textx
          python312Packages.simplejson
          python312Packages.intervaltree
        ];

        openxc7Packages = with openxc7Pkgs; [
          fasm
          fpga-assembler
          prjxray
          nextpnr-xilinx
        ];
      in {
        devShells = {
          default = pkgs.mkShell { name = "chisel"; inherit packages; };
          openxc7 = pkgs.mkShell {
            name = "openxc7";
            packages = packages ++ synthPackages ++ openxc7Packages;
            shellHook = nixpkgs.lib.concatStrings [
              "export NEXTPNR_XILINX_DIR=" openxc7Pkgs.nextpnr-xilinx.outPath "\n"
              "export NEXTPNR_XILINX_PYTHON_DIR=" openxc7Pkgs.nextpnr-xilinx.outPath "/share/nextpnr/python/\n"
              "export PRJXRAY_DB_DIR=" openxc7Pkgs.nextpnr-xilinx.outPath "/share/nextpnr/external/prjxray-db\n"
              "export PRJXRAY_PYTHON_DIR=" openxc7Pkgs.prjxray.outPath "/usr/share/python3/\n"
              ''export PYTHONPATH=''$PYTHONPATH:''$PRJXRAY_PYTHON_DIR:'' 
                openxc7Pkgs.fasm.outPath "/lib/python3.12/site-packages/:"
                pkgs.python312Packages.textx.outPath "/lib/python3.12/site-packages/:"
                pkgs.python312Packages.arpeggio.outPath "/lib/python3.12/site-packages/:"
                pkgs.python312Packages.pyyaml.outPath "/lib/python3.12/site-packages/:"
                pkgs.python312Packages.simplejson.outPath "/lib/python3.12/site-packages/:"
                pkgs.python312Packages.intervaltree.outPath "/lib/python3.12/site-packages/:"
                pkgs.python312Packages.sortedcontainers.outPath "/lib/python3.12/site-packages/:"
                "\n"
              "export PYPY3=" pkgs.pypy310.outPath "/bin/pypy3.10"
            ];
          };
        };
      });
}
