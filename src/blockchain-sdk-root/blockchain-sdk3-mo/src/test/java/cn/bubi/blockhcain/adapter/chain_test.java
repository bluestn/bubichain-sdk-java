/*
Copyright Bubi Technologies Co., Ltd. 2017 All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.bubi.blockhcain.adapter;


import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import cfca.sadk.algorithm.util.FileUtil;
import cn.bubi.baas.utils.encryption.BubiKey3;
import cn.bubi.baas.utils.encryption.BubiKeyType;
import cn.bubi.baas.utils.encryption.CertFileType;
import cn.bubi.baas.utils.encryption.utils.HashUtil;
import cn.bubi.baas.utils.encryption.utils.HexFormat;
import cn.bubi.baas.utils.encryption.utils.HttpKit;
import cn.bubi.blockchain.adapter.BlockChainAdapter;
import cn.bubi.blockchain.adapter.BlockChainAdapterProc;
import cn.bumo.blockchain.adapter3.Common.Signature;
import cn.bumo.blockchain.adapter3.Chain;
import cn.bumo.blockchain.adapter3.Overlay;
import cn.bumo.blockchain.adapter3.Chain.AccountPrivilege;
import cn.bumo.blockchain.adapter3.Chain.AccountThreshold;
import cn.bumo.blockchain.adapter3.Chain.Asset;
import cn.bumo.blockchain.adapter3.Chain.AssetProperty;
import cn.bumo.blockchain.adapter3.Chain.Operation;
import cn.bumo.blockchain.adapter3.Chain.OperationCreateAccount;
import cn.bumo.blockchain.adapter3.Chain.OperationIssueAsset;
import cn.bumo.blockchain.adapter3.Chain.OperationPayment;
import cn.bumo.blockchain.adapter3.Chain.OperationSetMetadata;
import cn.bumo.blockchain.adapter3.Chain.OperationSetSignerWeight;
import cn.bumo.blockchain.adapter3.Chain.OperationSetThreshold;
import cn.bumo.blockchain.adapter3.Chain.OperationTypeThreshold;
import cn.bumo.blockchain.adapter3.Chain.Signer;
import cn.bumo.blockchain.adapter3.Chain.SignerOrBuilder;
import cn.bumo.blockchain.adapter3.Chain.Transaction;
import cn.bumo.blockchain.adapter3.Chain.TransactionEnv;

public class chain_test {
	private BlockChainAdapter chain_message_one_;
	private TestThread test_thread = new TestThread();
	private Object object_;
	//private Timer timer_;
	private Logger logger_;
	public static void main(String[] argv) {
		chain_test chaintest = new chain_test();
		chaintest.Initialize();
		System.out.println("*****************start chain_message successfully******************");
		try {
			Thread.sleep(100000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		chaintest.Stop();
	}
	public void Stop() {
		chain_message_one_.Stop();
		test_thread.Stop();
	}
	//@Test
	public void Initialize() {
		
		logger_ = LoggerFactory.getLogger(BlockChainAdapter.class);
		object_ = new Object();
		chain_message_one_ = new BlockChainAdapter("ws://127.0.0.1:36003");
		chain_message_one_.AddChainResponseMethod(Overlay.ChainMessageType.CHAIN_HELLO_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainHello(msg, length);
			}
		});
		chain_message_one_.AddChainMethod(Overlay.ChainMessageType.CHAIN_TX_STATUS_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainTxStatus(msg, length);
			}
		});
		chain_message_one_.AddChainMethod(Overlay.ChainMessageType.CHAIN_PEER_MESSAGE_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainPeerMessage(msg, length);
			}
		});
		chain_message_one_.AddChainMethod(Overlay.ChainMessageType.CHAIN_LEDGER_HEADER_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainLedgerHeader(msg, length);
			}
		});
		chain_message_one_.AddChainMethod(Overlay.ChainMessageType.CHAIN_TX_ENV_STORE_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainTxEnvStore(msg, length);
			}
		});
		
		Overlay.ChainHello.Builder chain_hello = Overlay.ChainHello.newBuilder();
		chain_hello.setTimestamp(System.currentTimeMillis());
		if (!chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_HELLO.getNumber(), chain_hello.build().toByteArray())) {
			logger_.error("send hello failed");
		}
	}
	private void OnChainHello(byte[] msg, int length) {
		try {
			Overlay.ChainStatus chain_status = Overlay.ChainStatus.parseFrom(msg);
			System.out.println(chain_status);
		} catch (Exception e) {
			logger_.error(e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void OnChainPeerMessage(byte[] msg, int length) {
		try {
			Overlay.ChainPeerMessage chain_peer_message = Overlay.ChainPeerMessage.parseFrom(msg);
			System.out.print("chain peer message: " + chain_peer_message.getData().toStringUtf8() + ", src: " + 
			chain_peer_message.getSrcPeerAddr() + ", dest: ");
			for (int i = 0; i < chain_peer_message.getDesPeerAddrsCount(); i++) {
				System.out.print(chain_peer_message.getDesPeerAddrs(i));
				if (i != chain_peer_message.getDesPeerAddrsCount() - 1) {
					System.out.print(", ");
				}
				else {
					System.out.println("");
				}
			}
		} catch (InvalidProtocolBufferException e) {
			logger_.error(e.getMessage());
		}
		synchronized(object_) {
			object_.notifyAll();
		}
	}
	
	private void OnChainTxStatus(byte[] msg, int length) {
		try {
			Overlay.ChainTxStatus chain_tx_status = Overlay.ChainTxStatus.parseFrom(msg);
			if (chain_tx_status.getStatus() == Overlay.ChainTxStatus.TxStatus.FAILURE || chain_tx_status.getStatus() == Overlay.ChainTxStatus.TxStatus.COMPLETE) {
				System.out.println("hash: " + chain_tx_status.getTxHash() + ", status: " + chain_tx_status.getStatus() + ", desc: " + chain_tx_status.getErrorDesc() + ", receive time:" + System.currentTimeMillis());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void OnChainTxEnvStore(byte[] msg, int length) {
		try {
			Chain.TransactionEnvStore tranEnvStore = Chain.TransactionEnvStore.parseFrom(msg);
			System.out.println("hash:" + HexFormat.byteToHex(tranEnvStore.getHash().toByteArray()).toLowerCase() + ", error code: " + tranEnvStore.getErrorCode() + ", desc: " + tranEnvStore.getErrorDesc() + ", receive time:" + System.currentTimeMillis());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void OnChainLedgerHeader(byte[] msg, int length) {
		try {
			Chain.LedgerHeader ledger_header = Chain.LedgerHeader.parseFrom(msg);
			System.out.println("new ledger seq: " + ledger_header.getSeq() + ", close time:" + ledger_header.getCloseTime());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public BubiKey3 TestCreateAccount(String url, String srcAddress, String srcPrivate, String srcPublic, 
			int masterWeight, int threshold, BubiKeyType algorithm, CertFileType certFileType, String certFile, String password) {
		BubiKey3 bubikey_new = null;
		try {
			// get hash type
			String getHello = url + "/hello";
			String hello = HttpKit.post(getHello, "");
			JSONObject ho = JSONObject.parseObject(hello);
			Integer hash_type = ho.containsKey("hash_type") ? ho.getInteger("hash_type") : 0;
			
			// getAccount
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
			
			// generate new Account address, PrivateKey, publicKey
			if (algorithm == BubiKeyType.CFCA) {
				byte fileData[] = FileUtil.getBytesFromFile(certFile);
				bubikey_new = new BubiKey3(certFileType, fileData, password);
			}
			else {
				bubikey_new = new BubiKey3(algorithm);
				System.out.println("address: " + bubikey_new.getBubiAddress());
				System.out.println("private key: " + bubikey_new.getEncPrivateKey());
				System.out.println("public key: " + bubikey_new.getEncPublicKey());
			}
			
			// use src account sign
			BubiKey3 bubiKey_src = new BubiKey3(srcPrivate);
			
			// generate transaction
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder();
			Transaction.Builder tran = tranEnv.getTransactionBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			tran.setFee(500000);
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.CREATE_ACCOUNT);
			OperationCreateAccount.Builder createAccount = oper.getCreateAccountBuilder();
			createAccount.setDestAddress(bubikey_new.getBubiAddress());
			createAccount.setInitBalance(200000000);
			AccountPrivilege.Builder accountPrivilege = createAccount.getPrivBuilder();
			accountPrivilege.setMasterWeight(1);
			AccountThreshold.Builder accountThreshold = accountPrivilege.getThresholdsBuilder();
			accountThreshold.setTxThreshold(1);
			
			Signature.Builder signature  = tranEnv.addSignaturesBuilder();
			signature.setPublicKey(bubiKey_src.getEncPublicKey());
			byte[] sign_data = bubiKey_src.sign(tran.build().toByteArray());
			signature.setSignData(ByteString.copyFrom(sign_data));
			
			System.out.println("create account hash: " + HashUtil.GenerateHashHex(tran.build().toByteArray(), hash_type));
			
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return bubikey_new;
	}
	
	public void TestIssue(String url, BubiKey3 account) {
		try {
			String srcAddress = account.getBubiAddress();
			String srcPublic = account.getEncPublicKey();
			
			// get hash type
			String getHello = url + "/hello";
			String hello = HttpKit.post(getHello, "");
			JSONObject ho = JSONObject.parseObject(hello);
			Integer hash_type = ho.containsKey("hash_type") ? ho.getInteger("hash_type") : 0;
						
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
					
			// generate transaction
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
			Transaction.Builder tran = tranEnv.getTransactionBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			tran.setFee(500000);
			
		    // add operations
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.ISSUE_ASSET);
			OperationIssueAsset.Builder issuer = oper.getIssueAssetBuilder();
			issuer.setCode("coin");
			issuer.setAmount(100000);
			
		    // add signature list
			Signature.Builder signature  = tranEnv.addSignaturesBuilder();
			signature.setPublicKey(srcPublic);
			byte[] sign_data = account.sign(tran.build().toByteArray());
			signature.setSignData(ByteString.copyFrom(sign_data));
			
			System.out.println("issue hash: " + HashUtil.GenerateHashHex(tran.build().toByteArray(), hash_type));

			// send transaction
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	public void TestPayment(String url, BubiKey3 srcAccount, BubiKey3 desAccount) {
		try {
			// get hash type
			String getHello = url + "/hello";
			String hello = HttpKit.post(getHello, "");
			JSONObject ho = JSONObject.parseObject(hello);
			Integer hash_type = ho.containsKey("hash_type") ? ho.getInteger("hash_type") : 0;
						
			String srcAddress = srcAccount.getBubiAddress();
			String destAddress = desAccount.getBubiAddress();
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
			
			// generate transaction
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder();
			Transaction.Builder tran = tranEnv.getTransactionBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			tran.setFee(50000);
			
		    // add operations
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.PAYMENT);
			OperationPayment.Builder payment = oper.getPaymentBuilder();
			payment.setDestAddress(destAddress);
			Asset.Builder asset = payment.getAssetBuilder();
			asset.setAmount(10000);
			AssetProperty.Builder assetProperty = asset.getPropertyBuilder();
			assetProperty.setCode("coin");
			assetProperty.setIssuer(srcAddress);
			
		    // add signature list
			Signature.Builder signature  = tranEnv.addSignaturesBuilder();
			signature.setPublicKey(srcAccount.getEncPublicKey());
			byte[] sign_data = srcAccount.sign(tran.build().toByteArray());
			signature.setSignData(ByteString.copyFrom(sign_data));
			
			System.out.println("payment hash: " + HashUtil.GenerateHashHex(tran.build().toByteArray(), hash_type));

			// send transaction
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	public void TestSetMetadata(String url, BubiKey3 account, String key, String value) {
		try {
			String srcAddress = account.getBubiAddress();
			String srcPublic = account.getEncPublicKey();
			
			// get hash type
			String getHello = url + "/hello";
			String hello = HttpKit.post(getHello, "");
			JSONObject ho = JSONObject.parseObject(hello);
			Integer hash_type = ho.containsKey("hash_type") ? ho.getInteger("hash_type") : 0;
						
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
					
			// generate transaction
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
			Transaction.Builder tran = tranEnv.getTransactionBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			tran.setFee(500000);
			
		    // add operations
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.SET_METADATA);
			OperationSetMetadata.Builder setMetadata = oper.getSetMetadataBuilder();
			setMetadata.setKey(key);
			setMetadata.setValue(value);
			setMetadata.setDeleteFlag(false);
			
		    // add signature list
			Signature.Builder signature  = tranEnv.addSignaturesBuilder();
			signature.setPublicKey(srcPublic);
			byte[] sign_data = account.sign(tran.build().toByteArray());
			signature.setSignData(ByteString.copyFrom(sign_data));
			
			System.out.println("set metadata hash: " + HashUtil.GenerateHashHex(tran.build().toByteArray(), hash_type));

			// send transaction
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	public void TestSetSignerWeight(String url, BubiKey3 account, Map<String, Long> signers) {
		try {
			String srcAddress = account.getBubiAddress();
			String srcPublic = account.getEncPublicKey();
			
			// get hash type
			String getHello = url + "/hello";
			String hello = HttpKit.post(getHello, "");
			JSONObject ho = JSONObject.parseObject(hello);
			Integer hash_type = ho.containsKey("hash_type") ? ho.getInteger("hash_type") : 0;
						
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
					
			// generate transaction
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
			Transaction.Builder tran = tranEnv.getTransactionBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			tran.setFee(500000);
			
		    // add operations
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.SET_SIGNER_WEIGHT);
			OperationSetSignerWeight.Builder setMetadata = oper.getSetSignerWeightBuilder();
			setMetadata.setMasterWeight(10);
			for(Map.Entry<String, Long> signerWeight : signers.entrySet()) {
				Signer.Builder signer = setMetadata.addSignersBuilder();
				signer.setAddress(signerWeight.getKey());
				signer.setWeight(signerWeight.getValue().longValue());
			}
			
		    // add signature list
			Signature.Builder signature  = tranEnv.addSignaturesBuilder();
			signature.setPublicKey(srcPublic);
			byte[] sign_data = account.sign(tran.build().toByteArray());
			signature.setSignData(ByteString.copyFrom(sign_data));
			
			System.out.println("set signer weight hash: " + HashUtil.GenerateHashHex(tran.build().toByteArray(), hash_type));

			// send transaction
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	public void TestSetThreshold(String url, BubiKey3 account, Map<Operation.Type, Long> typeThresholds) {
		try {
			String srcAddress = account.getBubiAddress();
			String srcPublic = account.getEncPublicKey();
			
			// get hash type
			String getHello = url + "/hello";
			String hello = HttpKit.post(getHello, "");
			JSONObject ho = JSONObject.parseObject(hello);
			Integer hash_type = ho.containsKey("hash_type") ? ho.getInteger("hash_type") : 0;
						
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
					
			// generate transaction
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
			Transaction.Builder tran = tranEnv.getTransactionBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			tran.setFee(500000);
			
		    // add operations
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.SET_THRESHOLD);
			OperationSetThreshold.Builder setMetadata = oper.getSetThresholdBuilder();
			setMetadata.setTxThreshold(15);
			for(Map.Entry<Operation.Type, Long> threshold : typeThresholds.entrySet()) {
				OperationTypeThreshold.Builder typeThreshold = setMetadata.addTypeThresholdsBuilder();
				typeThreshold.setType(threshold.getKey());
				typeThreshold.setThreshold(threshold.getValue());
			}
			
		    // add signature list
			Signature.Builder signature  = tranEnv.addSignaturesBuilder();
			signature.setPublicKey(srcPublic);
			byte[] sign_data = account.sign(tran.build().toByteArray());
			signature.setSignData(ByteString.copyFrom(sign_data));
			
			System.out.println("set threshold hash: " + HashUtil.GenerateHashHex(tran.build().toByteArray(), hash_type));

			// send transaction
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	class TestThread implements Runnable {
		boolean enabled_ = true;
		Thread testThead_;
		TestThread() {
			testThead_ = new Thread(this);
			testThead_.start();
		}

		@Override
		public void run() {
			while(enabled_) {
				try {
					Thread.sleep(5000);
					// genesis
					String url = "http://127.0.0.1:36002";
					String privateKey = "privbtGQELqNswoyqgnQ9tcfpkuH8P1Q6quvoybqZ9oTVwWhS6Z2hi1B";
					String publicKey = "b001b6d3120599d19cae7adb6c5e2674ede8629c871cb8b93bd05bb34d203cd974c3f0bc07e5";
					String address = "buQdBdkvmAhnRrhLp4dmeCc2ft7RNE51c9EK";			
					
					System.out.println("======================= create account one =======================");
					BubiKey3 accountOne = TestCreateAccount(url, address, privateKey, publicKey, 10, 11, BubiKeyType.ED25519, null, null, null);
					Thread.sleep(10000);
					System.out.println("\n===================== account one issue coin =====================");
					TestIssue(url, accountOne);
					Thread.sleep(10000);
					System.out.println("\n\n======================= create account two =======================");
					BubiKey3 accountTwo = TestCreateAccount(url, address, privateKey, publicKey, 10, 11, BubiKeyType.ED25519, null, null, null);
					Thread.sleep(10000);
					System.out.println("\n\n============= account one pay account two 10000 coin =============");
					TestPayment(url, accountOne, accountTwo);
					Thread.sleep(10000);
					System.out.println("\n\n==================== account one set metadata ====================");
					TestSetMetadata(url, accountOne, "coin", "hello world");
					Thread.sleep(10000);
					System.out.println("\n\n================= account one set signers weight =================");
					Map<String, Long> signers = new HashMap<String, Long>();
					signers.put(accountTwo.getBubiAddress(), 10L);
					TestSetSignerWeight(url, accountOne, signers);
					Thread.sleep(10000);
					System.out.println("\n\n================= account one set type threshold =================");
					Map<Operation.Type, Long> typeThresholds = new HashMap<Operation.Type, Long>();
					typeThresholds.put(Operation.Type.CREATE_ACCOUNT, 15L);
					typeThresholds.put(Operation.Type.ISSUE_ASSET, 8L);
					typeThresholds.put(Operation.Type.SET_METADATA, 10L);
					TestSetThreshold(url, accountOne, typeThresholds);
					Thread.sleep(10000000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		public void Stop() {
			enabled_ = false;
			try {
				testThead_.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}

