package cn.bubi.test;

import org.bouncycastle.util.encoders.Base64;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import cfca.sadk.algorithm.util.FileUtil;
import cn.bubi.baas.utils.encryption.BubiKey;
import cn.bubi.baas.utils.encryption.BubiKeyType;
import cn.bubi.baas.utils.encryption.CertFileType;
import cn.bubi.baas.utils.encryption.utils.HashUtil;
import cn.bubi.baas.utils.encryption.utils.HttpKit;
import cn.bubi.blockchain.adapter3.Chain.*;

public class TestBubikey {
	public static void main(String[] args) {

		// test signature and verify
//		System.out.println("================ test b58 ed25519 ==================");
//		testB58_ED25519();
//		System.out.println("================ test b16 ed25519 ==================");
//		testB16_ED25519();
//		System.out.println("");
//		
//		System.out.println("\n\n================ test b58 eccsm2 ==================");
//		testB58_ECCSM2();
//		System.out.println("================ test b16 eccsm2 ==================");
//		testB16_ECCSM2();
//		System.out.println("");
		
//		System.out.println("\n\n================ test b58 cfca pfx ==================");
//		testB58_CFCA_pfx();
//		System.out.println("================ test b16 cfca pfx ==================");
//		testB16_CFCA_pfx();
//		System.out.println("");
		
//		System.out.println("\n\n================ test b58 cfca sm2 ==================");
//		testB58_CFCA_SM2();
//		System.out.println("================ test b16 cfca sm2 ==================");
//		testB16_CFCA_SM2();
//		System.out.println("");
//		
//		System.out.println("\n\n================ test b58 cfca jks ==================");
//		testB58_CFCA_JKS();
//		System.out.println("================ test b16 cfca jks ==================");
//		testB16_CFCA_JKS();
//		
//		// test create account transaction
//		System.out.println("\n\n================ teat create account ==================");
//		String url = "http://192.168.10.110:29333";
//		String privateKey = "c0015ed4d20945d47d0b8708bcad5b94f83a75504ee5f5d71d80fc8189aadf71203497";
//		String publicKey = "b001671bbc4fb156f701a2a4f8fbc331d27bc8861a43ae56bcfc5a3ae9bdbb03d1be13";
//		String address = "a0017bb37115637686a4efd6fabe8bfd74d695c3616515";
//		BubiKey bubiKey_new = TestCreateAccount(url, address, privateKey, publicKey, 10, 11, BubiKeyType.ED25519, CertFileType.SM2, 
//				"D:/bubi/Peer2.SM2", "cfca1234");
//		System.out.println(bubiKey_new.getB16Address());
//		Thread.sleep(2000);
//		BubiKey bubiKey_one = TestCreateAccount(url, bubiKey_new.getB16Address(), bubiKey_new.getB16PrivKey(), 
//				bubiKey_new.getB16PublicKey(), 10, 1, BubiKeyType.CFCA, CertFileType.SM2, "D:/bubi/Peer3.SM2", "cfca1234");
//		System.out.println(bubiKey_one.getB16Address());
	}
	
	public static BubiKey TestCreateAccount(String url, String srcAddress, String srcPrivate, String srcPublic, 
			int masterWeight, int threshold, BubiKeyType algorithm, CertFileType certFileType, String certFile, String password) {
		BubiKey bubikey_new = null;
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
				bubikey_new = new BubiKey(certFileType, fileData, password);
			}
			else {
				bubikey_new = new BubiKey(algorithm);
			}
			String newAccountAddress = bubikey_new.getB16Address();
			
			// use src account sign
			BubiKey bubiKey_src = new BubiKey(srcPrivate, srcPublic);
			
			
			// generate transaction
			Transaction.Builder tran = Transaction.newBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.CREATE_ACCOUNT);
			OperationCreateAccount.Builder createAccount = OperationCreateAccount.newBuilder();
			AccountPrivilege.Builder accountPrivilege = AccountPrivilege.newBuilder();
			accountPrivilege.setMasterWeight(masterWeight);
			AccountThreshold.Builder accountThreshold = AccountThreshold.newBuilder();
			accountThreshold.setTxThreshold(threshold);
			accountPrivilege.setThresholds(accountThreshold);
			
			createAccount.setPriv(accountPrivilege);
			createAccount.setDestAddress(newAccountAddress);
			oper.setCreateAccount(createAccount);
			
			// generate hex string of transaction's hash
			String hash = HashUtil.GenerateHashHex(tran.build().toByteArray(), hash_type);
			System.out.println("transaction hash: " + hash);
			
			// add transaction with signature
			JSONObject request = new JSONObject();
			JSONArray items = new JSONArray();
			JSONObject item = new JSONObject();
			item.put("transaction_blob", cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(tran.build().toByteArray()));
			JSONArray signatures = new JSONArray();
			JSONObject signature = new JSONObject();
			signature.put("sign_data", cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(bubiKey_src.sign(tran.build().toByteArray())));
			signature.put("public_key", srcPublic);
			signatures.add(signature);
			item.put("signatures", signatures);
			items.add(item);
			request.put("items", items);
			System.out.println(request.toJSONString());
			
			String submitTransaction = url + "/submitTransaction";
			String result = HttpKit.post(submitTransaction, request.toJSONString());
			System.out.println(result);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return bubikey_new;
	}
	
	public static void testB58_ED25519() {
		try {
			System.out.println("=========================BubiKeyType==================================");
			BubiKey bubiKey = new BubiKey(BubiKeyType.ED25519);
			System.out.println("bubuKey1 private key: " + bubiKey.getB58PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB58PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB58Address());
			
			System.out.println("bubuKey1 static public key: " + BubiKey.getB58PublicKey(bubiKey.getB58PrivKey()));
			System.out.println("bubuKey1 static address: " + BubiKey.getB58Address(bubiKey.getB58PublicKey()));
			
			System.out.println("=========================PrivateKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB58PrivKey(), null);
			System.out.println("bubuKey1 static public key: " + BubiKey.getB58PublicKey(bubiKey.getB58PrivKey()));
			System.out.println("bubuKey2 private key: " + bubiKey2.getB58PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB58PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB58Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(),bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB58PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB16_ED25519() {
		try {
			System.out.println("=========================BubiKeyType==================================");
			BubiKey bubiKey = new BubiKey(BubiKeyType.ED25519);
			System.out.println("bubuKey1 private key: " + bubiKey.getB16PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB16PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB16Address());
			
			System.out.println("bubuKey1 static public key: " + BubiKey.getB16PublicKey(bubiKey.getB16PrivKey()));
			System.out.println("bubuKey1 static address: " + BubiKey.getB16Address(bubiKey.getB16PublicKey()));
			
			System.out.println("=========================PrivateKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB16PrivKey(), null);
			System.out.println("bubuKey2 private key: " + bubiKey2.getB16PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB16PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB16Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(), bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB16PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB58_ECCSM2() {
		try {
			System.out.println("=========================BubiKeyType==================================");
			BubiKey bubiKey = new BubiKey(BubiKeyType.ECCSM2);
			System.out.println("bubuKey1 private key: " + bubiKey.getB58PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB58PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB58Address());
			System.out.println("bubuKey1 static public key: " + BubiKey.getB58PublicKey(bubiKey.getB58PrivKey()));
			System.out.println("bubuKey1 static address: " + BubiKey.getB58Address(bubiKey.getB58PublicKey()));
			
			System.out.println("=========================PrivateKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB58PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB58PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB58Address());
			
			Boolean verifyResult = false;
			System.out.println(verifyResult);
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(),bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB58PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB16_ECCSM2() {
		try {
			System.out.println("=========================BubiKeyType==================================");
			BubiKey bubiKey = new BubiKey(BubiKeyType.ECCSM2);
			System.out.println("bubuKey1 private key: " + bubiKey.getB16PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB16PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB16Address());
			System.out.println("bubuKey1 static public key: " + BubiKey.getB16PublicKey(bubiKey.getB16PrivKey()));
			System.out.println("bubuKey1 static address: " + BubiKey.getB16Address(bubiKey.getB16PublicKey()));
			
			System.out.println("=========================PrivateKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB16PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB16PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB16Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(), bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB16PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB58_CFCA_pfx() {
		
		try {
			System.out.println("=========================cert file data==================================");
			byte fileData[] = FileUtil.getBytesFromFile("D:/mytest_ex.pfx");
			BubiKey bubiKey = new BubiKey(CertFileType.PFX, fileData, "111111");
			System.out.println("bubuKey1 private key: " + bubiKey.getB58PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB58PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB58Address());
			
			System.out.println("bubuKey1 static address: " + BubiKey.getB58Address(bubiKey.getB58PublicKey()));
			
			System.out.println("=========================PrivateKey PublicKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB58PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB58PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB58Address());
			
			System.out.println("=========================cert file path==================================");
			BubiKey bubiKey3 = new BubiKey(CertFileType.PFX, "D:/mytest_ex.pfx", "111111");
			System.out.println("bubuKey3 private key: " + bubiKey3.getB58PrivKey());
			System.out.println("bubuKey3 public key: " + bubiKey3.getB58PublicKey());
			System.out.println("bubuKey3 address: " + bubiKey3.getB58Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(),bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB58PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB16_CFCA_pfx() {
		
		try {
			System.out.println("=========================cert file data==================================");
			byte fileData[] = FileUtil.getBytesFromFile("D:/test.pfx");
			BubiKey bubiKey = new BubiKey(CertFileType.PFX, fileData, "11111111");
			System.out.println("bubuKey1 private key: " + bubiKey.getB16PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB16PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB16Address());
			
			System.out.println("bubuKey1 static address: " + BubiKey.getB16Address(bubiKey.getB16PublicKey()));
			
			System.out.println("=========================PrivateKey PublicKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB16PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB16PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB16Address());
			
			System.out.println("=========================cert file path==================================");
			BubiKey bubiKey3 = new BubiKey(CertFileType.PFX, "D:/test.pfx", "11111111");
			System.out.println("bubuKey3 private key: " + bubiKey3.getB16PrivKey());
			System.out.println("bubuKey3 public key: " + bubiKey3.getB16PublicKey());
			System.out.println("bubuKey3 address: " + bubiKey3.getB16Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(), bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB16PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB58_CFCA_SM2() {	
		try {
			System.out.println("=========================cert file data==================================");
			byte fileData[] = FileUtil.getBytesFromFile("D:/bubi/GenesisAccount.SM2");
			BubiKey bubiKey = new BubiKey(CertFileType.SM2, fileData, "cfca1234");
			System.out.println("bubuKey1 private key: " + bubiKey.getB58PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB58PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB58Address());
			
			System.out.println("bubuKey1 static address: " + BubiKey.getB58Address(bubiKey.getB58PublicKey()));
			
			System.out.println("=========================PrivateKey PublicKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB58PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB58PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB58Address());
			
			System.out.println("=========================cert file path==================================");
			BubiKey bubiKey3 = new BubiKey(CertFileType.SM2, "D:/bubi/GenesisAccount.SM2", "cfca1234");
			System.out.println("bubuKey3 private key: " + bubiKey3.getB58PrivKey());
			System.out.println("bubuKey3 public key: " + bubiKey3.getB58PublicKey());
			System.out.println("bubuKey3 address: " + bubiKey3.getB58Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(),bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB58PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB16_CFCA_SM2() {	
		try {
			System.out.println("=========================cert file data==================================");
			byte fileData[] = FileUtil.getBytesFromFile("D:/bubi/GenesisAccount.SM2");
			BubiKey bubiKey = new BubiKey(CertFileType.SM2, fileData, "cfca1234");
			System.out.println("bubuKey1 private key: " + bubiKey.getB16PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB16PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB16Address());
			
			System.out.println("bubuKey1 static address: " + BubiKey.getB16Address(bubiKey.getB58PublicKey()));
			
			System.out.println("=========================PrivateKey PublicKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB16PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB16PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB16Address());
			
			System.out.println("=========================cert file path==================================");
			BubiKey bubiKey3 = new BubiKey(CertFileType.SM2, "D:/bubi/GenesisAccount.SM2", "cfca1234");
			System.out.println("bubuKey3 private key: " + bubiKey3.getB16PrivKey());
			System.out.println("bubuKey3 public key: " + bubiKey3.getB16PublicKey());
			System.out.println("bubuKey3 address: " + bubiKey3.getB16Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(), bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB16PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB58_CFCA_JKS() {	
		try {
			System.out.println("=========================cert file data==================================");
			byte fileData[] = FileUtil.getBytesFromFile("D:/peer.jks");
			BubiKey bubiKey = new BubiKey(fileData, "123456", "client");
			System.out.println("bubuKey1 private key: " + bubiKey.getB58PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB58PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB58Address());
			
			System.out.println("bubuKey1 static address: " + BubiKey.getB58Address(bubiKey.getB58PublicKey()));
			
			System.out.println("=========================PrivateKey PublicKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB58PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB58PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB58Address());
			
			System.out.println("=========================cert file path==================================");
			BubiKey bubiKey3 = new BubiKey("D:/peer.jks", "123456", "client");
			System.out.println("bubuKey3 private key: " + bubiKey3.getB58PrivKey());
			System.out.println("bubuKey3 public key: " + bubiKey3.getB58PublicKey());
			System.out.println("bubuKey3 address: " + bubiKey3.getB58Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(),bubiKey.getB58PrivKey(), bubiKey.getB58PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB58PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void testB16_CFCA_JKS() {	
		try {
			byte fileData[] = FileUtil.getBytesFromFile("D:/peer.jks");
			BubiKey bubiKey = new BubiKey(fileData, "123456", "client");
			System.out.println("bubuKey1 private key: " + bubiKey.getB16PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey.getB16PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey.getB16Address());
			
			System.out.println("bubuKey1 static address: " + BubiKey.getB16Address(bubiKey.getB58PublicKey()));
			
			System.out.println("=========================PrivateKey PublicKey==================================");
			BubiKey bubiKey2 = new BubiKey(bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("bubuKey2 private key: " + bubiKey2.getB16PrivKey());
			System.out.println("bubuKey2 public key: " + bubiKey2.getB16PublicKey());
			System.out.println("bubuKey2 address: " + bubiKey2.getB16Address());
			
			System.out.println("=========================cert file path==================================");
			BubiKey bubiKey4 = new BubiKey("D:/peer.jks", "123456", "client");
			System.out.println("bubuKey1 private key: " + bubiKey4.getB16PrivKey());
			System.out.println("bubuKey1 public key: " + bubiKey4.getB16PublicKey());
			System.out.println("bubuKey1 address: " + bubiKey4.getB16Address());
			
			String src = "test";
			byte[] sign = bubiKey2.sign(src.getBytes());
			byte[] sign_static = BubiKey.sign(src.getBytes(), bubiKey.getB16PrivKey(), bubiKey.getB16PublicKey());
			System.out.println("signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign));
			System.out.println("static signature: " + cn.bubi.baas.utils.encryption.utils.HexFormat.byteToHex(sign_static));
			System.out.println("verify: " + bubiKey2.verify(src.getBytes(), sign));
			System.out.println("static verify: " + BubiKey.verify(src.getBytes(), sign, bubiKey.getB16PublicKey()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
