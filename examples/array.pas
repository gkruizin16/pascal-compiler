program arrayProgram;
var a1: array[20..30] of integer;
var c1: array['a'..'e'] of integer;

begin
    a1[23] := 20171103;
    a1[24] := a1[23] + 2;
    writeln(a1[24]);

    c1['b'] := 10;
    writeln(c1['b'])
end.
