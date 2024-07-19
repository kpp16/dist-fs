# dist-fs

dist-fs is a Unix-style distributed filesystem written in Java, which uses Gradle for build automation. It also includes a Python client program for interacting with the filesystem. The system supports the following commands: pwd, mkdir, write, read, rm, and tree.


## Prerequisites

To get started with dist-fs, you need to have the following installed on your system:

* Java Development Kit (JDK) 21
* Python 3.11
* Gradle

## Project setup

Clone the Repository
```sh
git clone git@github.com:kpp16/dist-fs.git
cd dist-fs
```

Create a .env File

Create a .env file in the project root with the following variable. Replace the placeholder values with the actual addresses and ports of your worker servers:

```
BLOCK_HOSTS=host_name:port,hostname2:port,hostname3:port
```

### Run the Worker Program

Compile and run the worker program on each of your worker servers. The worker program is located in `worker/Client.java`

```sh
javac worker/Client.java
java worker.Client <port>
```

Replace <port> with the actual port number you want the worker to listen on.

### Build the Project

Navigate back to the project root and build the project using Gradle:
```sh
./gradlew build
```

### Running the Client

The client program is located in `pyclient/client.py`. The client acts as an interactive shell for the distributed filesystem.


## Usage

Navigate to the pyclient Directory `cd pyclient` and run the Client  `python3 client.py`


Once the client is running, you can use the following commands in the interactive shell:
    
* `pwd`: Print the current directory.
* `mkdir`: <directory_name>: Create a new directory.
* `write`: <file_name> <data>: Write data to a file.
* `read`: <file_name>: Read data from a file.
* `rm`: <file_name>: Delete file.
* `tree`: Display the directory tree.
* `exit`: Exit the shell.

### Warning
I was too lazy to fix the absolute paths, so for every command that you execute, make sure that you are in the correct folder. For example,
```sh
write folder1/folder2/hello.txt hello world
```
**WILL NOT WORK!** It will create the structure `folder1/folder2` but `hello.txt` will be stored in your current directory instead. Feel free to fix it!  


## Example
```sh
$ python3 client.py
Connected to server at localhost:8080
/@localhost:8080 > mkdir folder1
Directory folder1 created.
/@localhost:8080 > cd folder1
Changed directory to folder1
/folder1@localhost:8080 > pwd
/folder1
/folder1@localhost:8080 > write hello.txt hello world
File hello.txt created.
/folder1@localhost:8080 > read hello.txt
hello world
/folder1@localhost:8080 > delete hello.txt
Unknown command: delete
/folder1@localhost:8080 > rm hello.txt
Deleted file hello.txt.
/folder1@localhost:8080 > read hello.txt
Error: File does not exist
/folder1@localhost:8080 > pwd
/folder1
/folder1@localhost:8080 > cd ..
Changed directory to ..
/@localhost:8080 > write tst.txt this is a test file
File tst.txt created.
/@localhost:8080 > read tst.txt
this is a test file
/@localhost:8080 > exit
Exiting...
```

## TODO

1. Fix absolute paths as mentioned above.
2. Right now, only the data blocks (`FileBlock.java`) is being stored in different servers. Find a way to also store the Inode table and path mappings in different servers instead of storing them in RAM.
3. Make the FileSystemServer take the PORT as an argument.
4. Make the client.py also take the PORT as an argument. 
5. Implement `rmdir` command.
6. Make the tree command look pretty.

## Contributing
Contributions are welcome! Please fork the repository and open a pull request with your changes.

## License
This project is licensed under the MIT License. See the LICENSE file for details.
Contact

For any questions or issues, please open an issue on the GitHub repository or contact the maintainer.
Feel free to modify the instructions as needed based on your specific setup and requirements.
