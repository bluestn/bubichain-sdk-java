# __布比JAVA SDK使用文档__

##1 用途
该SDK用于与布比底层建立连接，可进行的操作：订阅消息、发送交易、传递消息、接收交易状态

##2 maven引用
###2.1 布比2.0版本
```pom
    <dependency>
        <groupId>cn.bubi.blockchain</groupId>
        <artifactId>blockchain-sdk</artifactId>
    	<version>2.0.10-SNAPSHOT</version>
    </dependency>
```
###2.2 布比3.0版本
```pom
    <dependency>
        <groupId>cn.bubi.blockchain</groupId>
        <artifactId>blockchain-sdk3</artifactId>
    	<version>2.0.10-SNAPSHOT</version>
	</dependency>
```
	
###3 构建BlockChainAdapter对象
BlockChainAdapter blockChainAdapter = new BlockChainAdapter(服务器URL);

例如：
```java
BlockChainAdapter blockChainAdapter = new BlockChainAdapter("ws://127.0.0.1:7053");
```

###4 绑定回调函数
blockChainAdapter.AddChainMethod(信息类型, 回调函数方法)

注意：绑定消息时注意布比2.0版与布比3.0版的消息路径的区别


####4.1 CHAIN_HELLO消息
该消息用于订制消息类型，解析消息时，需要用到ChainStatus数据类型，使用如下：

```java
// 布比2.0版
blockChainAdapter.AddChainMethod(Message.ChainMessageType.CHAIN_HELLO_VALUE, new BlockChainAdapterProc() {
	public void ChainMethod (byte[] msg, int length) {
		//处理 hello消息
		Message.ChainStatus chainStatus = Message.ChainStatus.parseFrom(msg);
	}
});
````
或
```java
// 布比3.0版
blockChainAdapter.AddChainMethod(Overlay.ChainMessageType.CHAIN_HELLO_VALUE, new BlockChainAdapterProc() {
	public void ChainMethod (byte[] msg, int length) {
		//处理 hello消息
		Overlay.ChainStatus chainStatus = Overlay.ChainStatus.parseFrom(msg);
	}
});
```

####4.2 CHAIN_TX_STATUS消息
该消息用于接收交易状态，解析消息时，需要用到ChainTxStatus数据类型，使用如下：
```java
// 布比2.0版
blockChainAdapter.AddChainMethod(Message.ChainMessageType.CHAIN_TX_STATUS_VALUE, new BlockChainAdapterProc() {
	public void ChainMethod (byte[] msg, int length) {
		//处理 hello消息
		Message.ChainTxStatus chainTxStatus = Message.ChainTxStatus.parseFrom(msg);
	}
});
```
或
```java
// 布比3.0版
blockChainAdapter.AddChainMethod(Overlay.ChainMessageType.CHAIN_TX_STATUS_VALUE, new BlockChainAdapterProc() {
	public void ChainMethod (byte[] msg, int length) {
		//处理 hello消息
		Overlay.ChainTxStatus chainTxStatus = Overlay.ChainTxStatus.parseFrom(msg);
	}
});
```

####4.2 CHAIN_PEER_MESSAGE消息
该消息用于节点之间传递消息，解析消息时，需要用到ChainPeerMessage数据类型，使用如下：
```java
// 布比2.0版
blockChainAdapter.AddChainMethod(Message.ChainMessageType.CHAIN_PEER_MESSAGE_VALUE, new BlockChainAdapterProc() {
	public void ChainMethod (byte[] msg, int length) {
		//处理 hello消息
		Message.ChainPeerMessage chainPeerMessage = Message.ChainPeerMessage.parseFrom(msg);
	}
});
````
或
```java
// 布比3.0版
blockChainAdapter.AddChainMethod(Overlay.ChainMessageType.CHAIN_PEER_MESSAGE_VALUE, new BlockChainAdapterProc() {
	public void ChainMethod (byte[] msg, int length) {
		//处理 hello消息
		Overlay.ChainPeerMessage chainPeerMessage = Overlay.ChainPeerMessage.parseFrom(msg);
	}
});
```

###5. 发送消息
blockChainAdapter.Send(信息类型， 消息内容);

注意：不同的消息类型，对应的消息的数据格式不同

####5.1 CHAIN_HELLO消息
该消息用于订制消息类型，需要用到ChainHello数据类型，使用如下：
```java
// 布比2.0版
Message.ChainHello.Builder chain_hello = Message.ChainHello.newBuilder();
chain_hello.setTimestamp(System.currentTimeMillis());
if (!blockChainAdapter.Send(Message.ChainMessageType.CHAIN_HELLO_VALUE, chain_hello.build().toByteArray())) {
    // 错误输出
}
````
或
```java
// 布比3.0版
Overlay.ChainHello.Builder chain_hello = Overlay.ChainHello.newBuilder();
chain_hello.setTimestamp(System.currentTimeMillis());
if (!blockChainAdapter.Send(Overlay.ChainMessageType.CHAIN_HELLO_VALUE, chain_hello.build().toByteArray())) {
    // 错误输出
}
```

####5.2 CHAIN_SUBMITTRANSACTION消息
该消息用于向底层发送交易，需要用到TransactionEnv等数据类型，使用如下：
```java
// 布比2.0版
Message.TransactionEnv.Builder env = Message.TransactionEnv.newBuilder(); 
Message.Transaction.Builder tran = Message.Transaction.newBuilder();
Message.Operation.Builder oper = tran.addOperationsBuilder();
Message.Signature.Builder sign = Message.Signature.newBuilder();
env.setTransaction(tran.build());
env.addSignatures(sign);
if (!blockChainAdapter.Send(Message.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, env.build().toByteArray())) {
	// 错误输出
}
```
或
```java
// 布比3.0版
Chain.TransactionEnv.Builder env = Chain.TransactionEnv.newBuilder(); 
Chain.Transaction.Builder tran = Chain.Transaction.newBuilder();
Chain.Operation.Builder oper = tran.addOperationsBuilder();
Common.Signature.Builder sign = Common.Signature.newBuilder();
if (!blockChainAdapter.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, env.build().toByteArray())) {
	// 错误输出
}
```

####5.3 CHAIN_PEER_MESSAGE消息
该消息用于底层节点之间传递消息，需要用到ChainPeerMessage数据类型，使用如下：
```java
// 布比2.0版
Message.ChainPeerMessage.Builder chain_peer_message = Message.ChainPeerMessage.newBuilder(); 
chain_peer_message.setSrcPeerAddr("发行该消息的节点地址");
chain_peer_message.addDesPeerAddrs("要发送到的节点的地址");
chain_peer_message.setData(ByteString.copyFromUtf8("待发送的数据"));
if (!blockChainAdapter.Send(Message.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, chain_peer_message.build().toByteArray())) {
	// 错误输出
}
```
或
```java
// 布比3.0版
Overlay.ChainPeerMessage.Builder chain_peer_message = Overlay.ChainPeerMessage.newBuilder(); 
chain_peer_message.setSrcPeerAddr("发行该消息的节点地址");
chain_peer_message.addDesPeerAddrs("要发送到的节点的地址");
chain_peer_message.setData(ByteString.copyFromUtf8("待发送的数据"));
if (!blockChainAdapter.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, chain_peer_message.build().toByteArray())) {
	// 错误输出
}
```

###6 发起交易的例子
需要添加依赖JAVA ENCRYPTIOIN:
```pom
<dependency>
	<groupId>cn.bubi.baas.utils</groupId>
	<artifactId>utils-encryption</artifactId>
	<version>1.3.12-SNAPSHOT</version>
</dependency>
```

####6.1 创建账号
需要引用JAVA ENCRYPTIOI：
```pom
    <dependency>
        <groupId>cn.bubi.baas.utils</groupId>
        <artifactId>utils-encryption</artifactId>
        <version>1.3.12-SNAPSHOT</version>
    </dependency>
```

#####6.1.1 布比2.0版
```java
try {
	String privateKey = "privbtZ1Fw5RRWD4ZFR6TAMWjN145zQJeJQxo3EXAABfgBjUdiLHLLHF";
	String address = "bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa";
	String httpRequest = "http://127.0.0.1:19333";
	
    // get tx sequence number
	String url = httpRequest + "/getAccount?address=" + address;
	String txSeq = null;
	txSeq = HttpKit.post(url, "");	
	JSONObject tx = JSONObject.parseObject(txSeq);
	String seq_str = tx.getJSONObject("result").getString("tx_seq");
	seq_ = Long.parseLong(seq_str);
    
    // generate new Account address, PrivateKey, publicKey
	BubiKey bubikey_new = new BubiKey(BubiKeyType.ED25519);
	
    // generate transaction
	Message.Transaction.Builder tran = Message.Transaction.newBuilder();
	tran.setSourceAddress("bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa");
	tran.setFee(1000);
	tran.setSequenceNumber(seq_ + 1);
	tran.setMetadata(ByteString.copyFromUtf8(String.valueOf(System.currentTimeMillis())));
	
    // add operation
	Message.Operation.Builder oper = tran.addOperationsBuilder();
	oper.setType(Message.Operation.Type.CREATE_ACCOUNT);
    oper.getCreateAccountBuilder().setDestAddress(bubikey_new.getB58Address());
    oper.getCreateAccountBuilder().setInitBalance(20000000);
				
    // add signature	
	Message.Signature.Builder sign = Message.Signature.newBuilder();
	BubiKey bubiKey = new BubiKey(privateKey);
	sign.setPublicKey(bubiKey.getB58PublicKey());
	sign.setSignData(ByteString.copyFrom(bubiKey.sign(tran.build().toByteArray())));
					
	Message.TransactionEnv.Builder env = Message.TransactionEnv.newBuilder(); 
	env.setTransaction(tran.build());
	env.addSignatures(sign);
					
    // send transaction
	chain_message_one_.Send(Message.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, env.build().toByteArray()));
} catch (Exception e1) {
	e1.printStackTrace();
}
```

#####6.1.1 布比3.0版
```java
try {
    String privateKey = "privbtZ1Fw5RRWD4ZFR6TAMWjN145zQJeJQxo3EXAABfgBjUdiLHLLHF";
	String address = "bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa";
	String httpRequest = "http://127.0.0.1:19333";
	
	// getAccount
	String getAccount = url + "/getAccount?address=" + address;
	String txSeq = HttpKit.post(getAccount, "");
	JSONObject tx = JSONObject.parseObject(txSeq);
	String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
	long nonce = Long.parseLong(seq_str);
			
	// generate new Account address, PrivateKey, publicKey
	if (algorithm == BubiKeyType.CFCA) {
		byte fileData[] = FileUtil.getBytesFromFile(certFile);
		bubikey_new = new BubiKey(certFileType, fileData, password);
	}
	else {
		bubikey_new = new BubiKey(algorithm);
	}
			
	// use src account sign
	BubiKey bubiKey_src = new BubiKey(srcPrivate);
			
			
	// generate transaction
	Transaction.Builder tran = Transaction.newBuilder();
	tran.setSourceAddress(srcAddress);
	tran.setNonce(nonce + 3);
	Operation.Builder oper = tran.addOperationsBuilder();
	oper.setType(Operation.Type.CREATE_ACCOUNT);
	OperationCreateAccount.Builder createAccount = OperationCreateAccount.newBuilder();
	createAccount.setDestAddress(bubikey_new.getB16Address());
	AccountPrivilege.Builder accountPrivilege = AccountPrivilege.newBuilder();
	accountPrivilege.setMasterWeight(1);
	AccountThreshold.Builder accountThreshold = AccountThreshold.newBuilder();
	accountThreshold.setTxThreshold(1);
	accountPrivilege.setThresholds(accountThreshold);
	createAccount.setPriv(accountPrivilege);
	oper.setCreateAccount(createAccount);
			
	Signature.Builder signature  = Signature.newBuilder();
	signature.setPublicKey(bubiKey_src.getB16PublicKey());
	byte[] sign_data = BubiKey.sign(tran.build().toByteArray(), srcPrivate);
	signature.setSignData(ByteString.copyFrom(sign_data));
			
	TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
	tranEnv.setTransaction(tran.build());
	tranEnv.addSignatures(signature.build());
			
	chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
```

####6.2 发行资产
#####6.2.1 布比2.0版
```java
try {
	String privateKey = "privbtZ1Fw5RRWD4ZFR6TAMWjN145zQJeJQxo3EXAABfgBjUdiLHLLHF";
	String address = "bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa";
	String httpRequest = "http://127.0.0.1:19333";
	// get tx sequence number
	String url = httpRequest + "/getAccount?address=" + address;
	String txSeq = null;
	txSeq = HttpKit.post(url, "");

	JSONObject tx = JSONObject.parseObject(txSeq);
	String seq_str = tx.getJSONObject("result").getString("tx_seq");
	seq_ = Long.parseLong(seq_str);
    
    // make transaction
	Message.Transaction.Builder tran = Message.Transaction.newBuilder();
	tran.setSourceAddress(address);
	tran.setFee(1000);
	tran.setSequenceNumber(seq_ + 1);
	tran.setMetadata(ByteString.copyFromUtf8(String.valueOf(System.currentTimeMillis())));
	
    // add operations
	Message.Operation.Builder oper = tran.addOperationsBuilder();
	oper.setType(Message.Operation.Type.ISSUE_ASSET);
	oper.getIssueAssetBuilder().getAssetBuilder().getPropertyBuilder().setCode("coin");
	oper.getIssueAssetBuilder().getAssetBuilder().getPropertyBuilder().setType(Message.AssetProperty.Type.IOU);
	oper.getIssueAssetBuilder().getAssetBuilder().getPropertyBuilder().setIssuer(address);
	oper.getIssueAssetBuilder().getAssetBuilder().setAmount(1);

    // set signature list
	Message.Signature.Builder sign = Message.Signature.newBuilder();			
	BubiKey bubiKey = new BubiKey(privateKey);
	sign.setPublicKey(bubiKey.getB58PublicKey());
	sign.setSignData(ByteString.copyFrom(bubiKey.sign(tran.build().toByteArray())));
					
	Message.TransactionEnv.Builder env = Message.TransactionEnv.newBuilder(); 
	env.setTransaction(tran.build());
	env.addSignatures(sign);

    // send transaction
	chain_message_one_.Send(Message.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, env.build().toByteArray()));
} catch (Exception e1) {
	e1.printStackTrace();
}
````

#####6.2.2 布比3.0版
```java
try {
	String privateKey = "privbtZ1Fw5RRWD4ZFR6TAMWjN145zQJeJQxo3EXAABfgBjUdiLHLLHF";
	String address = "bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa";
	String httpRequest = "http://127.0.0.1:19333";
	String getAccount = url + "/getAccount?address=" + address;
	String txSeq = HttpKit.post(getAccount, "");
	JSONObject tx = JSONObject.parseObject(txSeq);
	String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
	long nonce = Long.parseLong(seq_str);
			
	// generate transaction
	Transaction.Builder tran = Transaction.newBuilder();
	tran.setSourceAddress(srcAddress);
	tran.setNonce(nonce + 1);
	
    // add operations
	Operation.Builder oper = tran.addOperationsBuilder();
	oper.setType(Operation.Type.ISSUE_ASSET);
	OperationIssueAsset.Builder issuer = OperationIssueAsset.newBuilder();
	issuer.setCode("coin");
	issuer.setAmount(1);
	oper.setIssueAsset(issuer);
	
    // add signature list
	Signature.Builder signature  = Signature.newBuilder();
	signature.setPublicKey(srcPublic);
	byte[] sign_data = BubiKey.sign(tran.build().toByteArray(), srcPrivate);
	signature.setSignData(ByteString.copyFrom(sign_data));
			
	TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
	tranEnv.setTransaction(tran.build());
	tranEnv.addSignatures(signature.build());

	// send transaction
	chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
} catch (Exception e1) {
	e1.printStackTrace();
}
```

####6.3 转账
#####6.3.1 布比2.0版
```java
try {
	String privateKey = "privbtZ1Fw5RRWD4ZFR6TAMWjN145zQJeJQxo3EXAABfgBjUdiLHLLHF";
	String address = "bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa";
	String destAddress = "bubiQ0i2558GmfnBREe87ZagdkKsfeJh5HHijops";
	String httpRequest = "http://127.0.0.1:19333";
	
    // get tx sequence number
	String url = httpRequest + "/getAccount?address=" + address;
	String txSeq = null;
	txSeq = HttpKit.post(url, "");
	JSONObject tx = JSONObject.parseObject(txSeq);
	String seq_str = tx.getJSONObject("result").getString("tx_seq");
	seq_ = Long.parseLong(seq_str);
	
    // generate transaction
	Message.Transaction.Builder tran = Message.Transaction.newBuilder();
	tran.setSourceAddress(address);
	tran.setFee(1000);
	tran.setSequenceNumber(seq_ + 1);
	tran.setMetadata(ByteString.copyFromUtf8(String.valueOf(System.currentTimeMillis())));
	
    // add operations
	Message.Operation.Builder oper = tran.addOperationsBuilder();
	oper.setType(Message.Operation.Type.PAYMENT);
    oper.getPaymentBuilder().setDestAddress(destAddress);
    oper.getPaymentBuilder().getAssetBuilder().getPropertyBuilder().setType(Message.AssetProperty.Type.IOU);
    oper.getPaymentBuilder().getAssetBuilder().getPropertyBuilder().setIssuer(address);
    oper.getPaymentBuilder().getAssetBuilder().getPropertyBuilder().setCode("coin");
    oper.getPaymentBuilder().getAssetBuilder().setAmount(1);
					
    // add signature list
	Message.Signature.Builder sign = Message.Signature.newBuilder();
	BubiKey bubiKey = new BubiKey(privateKey);
	sign.setPublicKey(bubiKey.getB58PublicKey());
	sign.setSignData(ByteString.copyFrom(bubiKey.sign(tran.build().toByteArray())));
					
	Message.TransactionEnv.Builder env = Message.TransactionEnv.newBuilder(); 
	env.setTransaction(tran.build());
	env.addSignatures(sign);
	
    // send transaction
	chain_message_one_.Send(Message.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, env.build().toByteArray());
} catch (Exception e1) {
	e1.printStackTrace();
}
```

#####6.3.2 布比3.0版
```java
try {
	String privateKey = "privbtZ1Fw5RRWD4ZFR6TAMWjN145zQJeJQxo3EXAABfgBjUdiLHLLHF";
	String address = "bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa";
	String destAddress = "bubiQ0i2558GmfnBREe87ZagdkKsfeJh5HHijops";
	String httpRequest = "http://127.0.0.1:19333";
	
    // get tx sequence number
	String getAccount = url + "/getAccount?address=" + address;
	String txSeq = HttpKit.post(getAccount, "");
	JSONObject tx = JSONObject.parseObject(txSeq);
	String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
	long nonce = Long.parseLong(seq_str);
					
	// generate transaction
	Transaction.Builder tran = Transaction.newBuilder();
	tran.setSourceAddress(srcAddress);
	tran.setNonce(nonce + 1);
			
    // add operations
	Operation.Builder oper = tran.addOperationsBuilder();
	oper.setType(Operation.Type.PAYMENT);
	OperationPayment.Builder payment = OperationPayment.newBuilder();
	payment.setDestAddress(destAddress);
	Asset.Builder asset = Asset.newBuilder();
	asset.setAmount(1);
	AssetProperty.Builder assetProperty = AssetProperty.newBuilder();
	assetProperty.setCode("coin");
	assetProperty.setIssuer(address);
	asset.setProperty(assetProperty);
	
    // add signature list
	Signature.Builder signature  = Signature.newBuilder();
	signature.setPublicKey(srcPublic);
	byte[] sign_data = BubiKey.sign(tran.build().toByteArray(), srcPrivate);
	signature.setSignData(ByteString.copyFrom(sign_data));
					
	TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
	tranEnv.setTransaction(tran.build());
	tranEnv.addSignatures(signature.build());

	// send transaction
	chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
} catch (Exception e1) {
	e1.printStackTrace();
}
```

