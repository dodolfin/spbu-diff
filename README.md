# Programming Fundamentals course. Mathematics and Computer Science Faculty at SPbU

## Project #1. Diff utility
[Task (in russian)](./TASK.md)

### Usage
`java -jar diff.jar [OPTIONS] FILE1 FILE2` (run from root of the project directory)

Command-line utility diff compares FILE1 and FILE2 files and prints difference between them as
a sequence of added, deleted or changed lines. Output format is defined by OPTIONS.

### Input
Two file paths (absolute or relative) are given. The size of the file should be 10 Mb or less with 10000 or less lines.

### File processing
diff finds Largest Common Sequence of lines in two files. The algorithm takes time of O(NM), where N and M are the number
of lines in first and second file correspondingly.

### Output
Output is done through the means of command-line (standard output). Use `... diff.jar ... > out.txt` to redirect output to file `out.txt`.

If two files are same, output is empty. Otherwise, if OPTIONS is empty, output format is „normal“.
Different output formats are not compatible. All output formats and OPTIONS that trigger them are described below.

### „Unified“ output format
OPTION: `-u[CONTEXT_LINES] --unified=[CONTEXT_LINES]`

„Unified“ output format (used in GitHub).

First two lines are the names of compared files and the time of last modification of each
file. Then added/deleted/changed blocks are printed. Each block contains as much as possible, but
no more than CONTEXT_LINES (3 by default) context lines around it (those are common lines, they are not printed in „normal“ mode).
If blocks are overlapping, they are printed merged.

Each block is preceded by line describing changes in the following
format: `@@ -s1,l1 +s2,l2 @@` , where `s1` is the beginning of the block in first
file relative terms, `l1` is the number of lines in the block which are present in the first file. `s2` and `l2` are defined
similar.

### „Normal“ output format
OPTION: `-n --normal`

„Normal“ output format (default output format in diff utility for Linux).

Changed blocks are displayed without context around them; deleted and added lines are preceded with `<` and `>` signs respectively.

Each block is preceded by line which describes changes in the following format:
`f1r|op|f2r` (without `|` in actual output), where `op` is the type of operation (`a` for addition, `d` for deletion, `c` for changing),
`f1r` describes a range in the first file from where lines were deleted or where would lines from second file appear
if we would insert them in the first file. `f2r` describes the same rang for second file.

### Plain output format
OPTION: `--plain`

Plain output format.

Prints two files merged. Added lines are preceded by `+`, deleted lines are preceded by `-`.

### Getting help
Use OPTION `--help` to show usage and options description and terminate the program.

### Testing and test results
While in IntelliJ IDEA, to run all tests choose folder with tests (`./test`) in „Project“ tab and press `Ctrl+Shift+F10` (or choose
this action in the context menu).

`test_files/small_files` contains small files for testing program.
Bash script `test_on_small_files.sh` in the root directory prints all files and shows diff output on them.

In `test_files/large_files` folder large (a lot of lines) files are stored. I used them and UNIX implementantion of `patch` utility 
to test if my program is suitable as a real diff tool. 

Results: since development was done on Windows, files have CRLF (`\r\n`) lines endings. `patch` isn't able to work with that. 
So I changed line endings to LF (`\n`) and then run my program again. On a 10-Point scale my program scores 6/10: at least one block of original file is patched with some kind of error,
especially when using „unified“ output format.