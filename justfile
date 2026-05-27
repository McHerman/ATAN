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

# Run a single test case within a class (e.g. just test-case ATA8.LoadTest "loads correctly")
test-case class name:
    mill ATAN.test.testOnly ATA8.{{class}} -- -z {{quote(name)}}

test-case-trace class name:
    mill ATAN.test.testOnly ATA8.{{class}} -- -DemitVcd=1 -z {{quote(name)}}

# Generate SystemVerilog
verilog:
    mill ATAN.run

# Regenerate FlatBuffer Java classes from schema (requires flatc)
flatbuffer:
    flatc --java -o assembler/src/main/java eaac_program.fbs
    find assembler/src/main/java -name "*.java" -exec sed -i 's/Constants\.FLATBUFFERS_[0-9_]*();//' {} +

# Clean build artifacts
clean:
    mill clean
