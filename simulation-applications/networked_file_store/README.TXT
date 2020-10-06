What is this:

	+ A simple multi-threaded network server for storing and querying files
	
What is it good for:

	+ quickly storing any file type using the FileStoreOperation protocol

Software requirements:

	+ java 8
	+ log4j2 - https://logging.apache.org/log4j/2.x/download.html
	+ derby db - https://db.apache.org/derby/releases/release-10.14.2.0.cgi

Server Usage:

	+ To launch FileStore: java -cp <setup class path args> com.bbn.filestore.FileStore [optional path/file of a file store location]
	
Example clients:

	+ com.bbn.filestore.clients.test.Publisher - a FileStoreOperation publisher that creates and uploads fake FileStoreOperation objects
	+ com.bbn.filestore.clients.test.Query - a FileQueryOperation query client that queries stored file objects by publisher name
	
Key supporting components: 

	+ File Store project's "wire" protocol: See objects in com.bbn.filestore.protocol
	+ ThreadLimitedRequestPerConnectionObjectServer class: implements a thread-per-request network reactor for processing file store's wire protocol
	+ FileDatabase class: implements a single table database for storing and retrieving file data
