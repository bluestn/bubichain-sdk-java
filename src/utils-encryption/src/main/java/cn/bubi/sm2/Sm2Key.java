package cn.bubi.sm2;

import java.math.BigInteger;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Random;
import cn.bubi.sm3.SM3Digest;

/*
 根据《SM2椭圆曲线公钥密码算法 》（国家密码管理局 2010年12月）编写。
 注意：全是中文注释
 布比（北京）网络技术有限公司
 */

public class Sm2Key {
	// 私钥
	private BigInteger dA_;

	// 椭圆曲线参数
	ECParameterSpec spec_;

	// 公钥点
	ECPoint pA_;

	// nlen_ 有限域阶n的字节数
	int nlen_;
	static public byte[] fixedLen(BigInteger x, int len){
		byte[] out = new byte[len];
		byte[] tmp = x.toByteArray();
		
		if(tmp.length <= len){
			System.arraycopy(tmp, 0, out, len - tmp.length, tmp.length);
		}else{
			System.arraycopy(tmp, tmp.length - len, out, 0, len);
		}
		return out;
	}
	// 根据已有私钥对象构建
	public Sm2Key(byte[] skeybyte, ECParameterSpec spec) {
		spec_ = spec;
		BigInteger a = spec_.getCurve().getA();
		ECFieldFp fd = (ECFieldFp) spec_.getCurve().getField();
		BigInteger p = fd.getP();

		byte[] buff = new byte[skeybyte.length + 1];
		buff[0] = 0;
		System.arraycopy(skeybyte, 0, buff, 1, skeybyte.length);
		dA_ = new BigInteger(buff);

		//System.out.println(dA_.toString(16));

		pA_ = PointMul(dA_, spec_.getGenerator(), p, a);
		byte[] tmp = ((ECFieldFp) (spec_.getCurve().getField())).getP().toByteArray();
		if (tmp[0] == 0) {
			nlen_ = tmp.length - 1;
		} else {
			nlen_ = tmp.length;
		}
	}

	// 随机产生一个私钥
	public Sm2Key(ECParameterSpec spec) {
		spec_ = spec;

		byte[] tmp = ((ECFieldFp) (spec_.getCurve().getField())).getP().toByteArray();
		if (tmp[0] == 0) {
			nlen_ = tmp.length - 1;
		} else {
			nlen_ = tmp.length;
		}

		// 随机私钥
		java.util.Random random = new java.util.Random();
		ECFieldFp fp = (ECFieldFp) spec_.getCurve().getField();
		BigInteger a = spec_.getCurve().getA();

		BigInteger p = fp.getP();

		int bitlen = fp.getP().bitLength();
		do {

			dA_ = new BigInteger(bitlen, random);
			if (dA_.compareTo(BigInteger.ZERO) <= 0) {
				continue;
			}

			pA_ = PointMul(dA_, spec_.getGenerator(), p, a);

			if (pA_.getAffineX().bitLength() <= nlen_ - 8 || pA_.getAffineY().bitLength() <= nlen_ - 8) {
				continue;
			}
			break;
		} while (true);

	}

	// 得到公钥
	public byte[] GetPkeyByte() {
		BigInteger x = pA_.getAffineX();
		BigInteger y = pA_.getAffineY();

		byte[] xbyte = fixedLen(x, nlen_);
		byte[] ybyte = fixedLen(y, nlen_);

		byte[] out = new byte[2 * nlen_ + 1];
		byte PC = 4;
		out[0] = PC;
		System.arraycopy(xbyte, 0, out, 1, nlen_);
		System.arraycopy(ybyte, 0, out, 1 + nlen_, nlen_);
		return out;
	}

	private static ECPoint PkeyByte2Point(ECParameterSpec spec, byte[] pb) {
		BigInteger P = ((ECFieldFp) spec.getCurve().getField()).getP();

		byte PC = pb[0];
		int nlen = (int) Math.ceil(P.bitLength() / 8);
		byte[] xb = new byte[nlen + 1];
		byte[] yb = new byte[nlen + 1];

		switch (PC) {
		case 2:
			// BigInteger a = spec.getCurve().getA();
			// BigInteger b = spec.getCurve().getB();
			// alpha = (Xp3 + aXp +b)mod p
			// BigInteger alpha = x.pow(3).add(a.multiply(x)).add(b).mod(P);
			break;
		case 3:
			break;
		case 4:
			System.arraycopy(pb, 1, xb, 1, nlen);
			System.arraycopy(pb, 1 + nlen, yb, 1, nlen);
		default:
			break;
		}

		BigInteger x = new BigInteger(xb);
		BigInteger y = new BigInteger(yb);
		return new ECPoint(x, y);
	}

	public byte[] GetSkeyByte() {

		byte[] da = fixedLen(dA_,nlen_); 
		return da;
	}

	// 签名函数
	public byte[] Sign(byte[] id, byte[] msg) {
		BigInteger a = spec_.getCurve().getA();
		ECFieldFp fd = (ECFieldFp) spec_.getCurve().getField();
		BigInteger p = fd.getP();

		// 得到M^ = ZA||M
		ECPoint pA = PointMul(dA_, spec_.getGenerator(), p, a);

		byte[] ZA = GetZA(spec_, pA, id);
		//System.out.println("ZA=" + bytesToHex(ZA));

		byte[] M = new byte[msg.length + ZA.length];
		System.arraycopy(ZA, 0, M, 0, ZA.length);
		System.arraycopy(msg, 0, M, ZA.length, msg.length);

		// 第二步 e=Hv(M^)
		byte[] ebytes = new byte[nlen_ + 1];
		ebytes[0] = 0;
		byte[] h = SM3Digest.Hash(M);
		System.arraycopy(h, 0, ebytes, 1, h.length);

		BigInteger e = new BigInteger(bytesToHex(ebytes), 16);
		
		//System.out.println("e=" + bytesToHex(h));
		
		BigInteger n = spec_.getOrder();

		while (true) {
			// 第三步 产生随机数k [1,n-1]

			Random random = new java.util.Random();
			BigInteger K = new BigInteger(n.bitLength() - 1, random);

			if (K.compareTo(n) == 0 || K.compareTo(BigInteger.ZERO) == 0) {
				continue;
			}

			// BigInteger K = new
			// BigInteger("6CB28D99385C175C94F94E934817663FC176D925DD72B727260DBAAE1FB2F96F",
			// 16);
			// 第四步 计算pt1(x1,y1) = [K]G这个点
			ECPoint G = spec_.getGenerator();
			ECPoint pt1 = PointMul(K, G, p, a);
			BigInteger x1 = pt1.getAffineX();

			// 第五步 计算 r = (e + x1) mod n
			BigInteger r = x1.add(e).mod(n);
			// r = r.add(n).mod(n);

			
			// 确保r!=0 且 r+k!=n 也就是 (r+k) != 0 mod n
			if (r.add(K).mod(n).equals(BigInteger.ZERO)) {
				continue;
			}

			// 第六步 计算 s = ((1 + d)^-1 * (k - rd)) mod n
			BigInteger tmp1 = dA_.add(BigInteger.ONE).modInverse(n);
			BigInteger tmp2 = K.subtract(r.multiply(dA_).mod(n)).mod(n);
			BigInteger s = tmp1.multiply(tmp2).mod(n);

			if (s.equals(BigInteger.ZERO)) {
				continue;
			}


			byte[] rb = fixedLen(r, nlen_) ;
			byte[] sb = fixedLen(s, nlen_); 
			byte[] sig = new byte[2 * nlen_];

			System.arraycopy(rb, 0, sig, 0, nlen_);
			System.arraycopy(sb, 0, sig, nlen_, nlen_);
			return sig;
		}

	}

	// 签名验证
	public static boolean Verify(ECParameterSpec spec, byte[] id, byte[] msg, ECPoint pkey, Sm2Signature sig) {
		BigInteger a = spec.getCurve().getA();
		ECFieldFp fd = (ECFieldFp) spec.getCurve().getField();
		BigInteger p = fd.getP();
		do {
			// 第一步 r在[1,n-1]范围
			BigInteger order = spec.getOrder();
			if (sig.r.compareTo(order) >= 0 || sig.r.compareTo(BigInteger.ONE) < 0) {
				break;
			}

			// 第一步 s在[1,n-1]范围
			if (sig.s.compareTo(order) >= 0 || sig.s.compareTo(BigInteger.ONE) < 0) {
				break;
			}

			// 第三步 计算M^ = ZA||M
			byte[] ZA = GetZA(spec, pkey, id);
			byte[] M = new byte[ZA.length + msg.length];
			System.arraycopy(ZA, 0, M, 0, ZA.length);
			System.arraycopy(msg, 0, M, ZA.length, msg.length);

			// 第四步 计算e=Hv(M^)
			byte[] stre = SM3Digest.Hash(M);
			BigInteger e = new BigInteger(bytesToHex(stre), 16);

			// 第五步 计算t=(r'+s')mod n
			BigInteger t = sig.r.add(sig.s).mod(order);
			if (t.compareTo(BigInteger.ZERO) == 0) {
				break;
			}

			// 第六步 计算(x1,y1) = [s]G + [t]PA
			ECPoint G = spec.getGenerator();
			ECPoint tmp1 = PointMul(sig.s, G, p, a);
			ECPoint tmp2 = PointMul(t, pkey, p, a);
			ECPoint tmPoint = PointAdd(tmp1, tmp2, p, a);

			// 第七步 R=(e' + x1') 验证R==r'?
			BigInteger R = e.add(tmPoint.getAffineX()).mod(order);

			if (R.compareTo(sig.r) != 0) {
				break;
			}

			return true;

		} while (false);
		return false;
	}

	public static boolean Verify(byte[] msg, byte[] pkey, byte[] strsig, byte[] id, ECParameterSpec spec) {

		int len = strsig.length / 2;
		byte[] r = new byte[len + 1];
		byte[] s = new byte[len + 1];
		System.arraycopy(strsig, 0, r, 1, len);
		System.arraycopy(strsig, len, s, 1, len);
		Sm2Signature sig = new Sm2Signature(new BigInteger(r), new BigInteger(s));

		ECPoint pt = PkeyByte2Point(spec, pkey);
		return Verify(spec, id, msg, pt, sig);
	}

	//
	public static String bytesToHex(byte[] raw) {
		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(2 * raw.length);
		for (final byte b : raw) {
			hex.append(Character.forDigit((b & 0xF0) >> 4, 16)).append(Character.forDigit((b & 0x0F), 16));
		}
		return hex.toString();
	}

	// 计算ZA,椭圆曲线参数，公钥点，身份标识
	private static byte[] GetZA(ECParameterSpec spec, ECPoint pA, byte[] id) {
		int LENGTH = 32;
		
		byte[] za = new byte[256];
		int etlen = id.length * 8;
		// 拼接ENTLA
		int pos = 0;
		za[0] = (byte) (etlen >> 8);
		za[1] = (byte) (etlen & 0xFF);

		pos += 2;
		// 拼接用户ID
		System.arraycopy(id, 0, za, pos, id.length);
		pos += id.length;

		// 拼接a
		byte[] a = fixedLen(spec.getCurve().getA(), LENGTH);
		System.arraycopy(a, 0, za, pos, a.length);
		pos += a.length;

		// 拼接b
		byte[] b = fixedLen(spec.getCurve().getB(), LENGTH);
		System.arraycopy(b, 0, za, pos, b.length);
		pos += b.length;

		// 拼接xG
		byte[] xG = fixedLen(spec.getGenerator().getAffineX(), LENGTH);
		System.arraycopy(xG, 0, za, pos, xG.length);
		pos += xG.length;


		// 拼接yG
		byte[] yG = fixedLen(spec.getGenerator().getAffineY(), LENGTH);
		System.arraycopy(yG, 0, za, pos, yG.length);
		pos += yG.length;

		// 拼接xA
		byte[] xA = fixedLen(pA.getAffineX(), LENGTH); 
		System.arraycopy(xA, 0, za, pos, xA.length);
		pos += xA.length;

		// 拼接yA
		byte[] yA = fixedLen(pA.getAffineY(), LENGTH);
		System.arraycopy(yA, 0, za, pos, yA.length);
		pos += yA.length;

		byte[] tmp = new byte[pos];
		System.arraycopy(za, 0, tmp, 0, pos);
		//System.out.println("za=" + bytesToHex(tmp));
		
		return SM3Digest.Hash(tmp);
	}

	// 定义点加运算
	public static ECPoint PointAdd(ECPoint p1, ECPoint p2, BigInteger p, BigInteger a) {
		BigInteger x1 = p1.getAffineX();
		BigInteger y1 = p1.getAffineY();

		BigInteger x2 = p2.getAffineX();
		BigInteger y2 = p2.getAffineY();

		// System.out.println("p:" + p.toString(10));
		// System.out.println("a:" + a.toString(10));
		// System.out.println("x1:" + x1.toString(10));
		// System.out.println("y1:" + y1.toString(10));

		if (p1.equals(ECPoint.POINT_INFINITY)) {
			return p2;
		}

		if (p2.equals(ECPoint.POINT_INFINITY)) {
			return p1;
		}

		// 互逆相加
		if (x1.equals(x2) && (y1.add(y2).mod(p).equals(0))) {
			return ECPoint.POINT_INFINITY;
		} else {
			// 相异非互逆
			// λ = (y2-y1)/(x2-x1) 若 x1!=x2
			// λ = (3*x1^2 + a)/(2*y1) 若 x1=x2

			BigInteger lambda = new BigInteger("0");
			if (x1.equals(x2)) {
				// System.out.println("x1 == x2");
				BigInteger tmp1 = x1.modPow(new BigInteger("2"), p).multiply(new BigInteger("3")).add(a);
				BigInteger tmp2 = y1.multiply(new BigInteger("2")).modInverse(p);

				lambda = tmp1.multiply(tmp2).mod(p);
			} else {
				// System.out.println("x1 != x2");
				lambda = y2.subtract(y1).multiply(x2.subtract(x1).modInverse(p)).mod(p);
			}

			BigInteger x3 = lambda.modPow(new BigInteger("2"), p).subtract(x1).mod(p).subtract(x2).mod(p);

			BigInteger y3 = x1.subtract(x3).mod(p).multiply(lambda).mod(p).subtract(y1).mod(p);
			ECPoint pt = new ECPoint(x3, y3);
			return pt;
		}
	}

	// 定义倍点运算
	public static ECPoint PointMul(BigInteger k, ECPoint pt, BigInteger p, BigInteger a) {
		int l = k.bitLength();
		ECPoint Q = ECPoint.POINT_INFINITY;

		for (int j = l - 1; j >= 0; j--) {
			Q = PointAdd(Q, Q, p, a);
			if (k.testBit(j)) {
				Q = PointAdd(Q, pt, p, a);
			}
		}
		return Q;
	}
}
