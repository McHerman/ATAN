default:
    @just --list

# Compile all Chisel sources and emit SystemVerilog
comp:
    mill ATAN.compile

# Run all tests
test:
    mill ATAN.test

# Run a specific test class (e.g. just test-only ATA8.LoadTest)
test-only class:
    mill ATAN.test.testOnly ATA8.{{class}}

test-only-trace class:
    mill ATAN.test.testOnly ATA8.{{class}} -- -DemitVcd=1

# Generate SystemVerilog
verilog:
    mill ATAN.run

# Clean build artifacts
clean:
    mill clean
