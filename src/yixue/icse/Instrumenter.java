package yixue.icse;

//这里现在是你的优先分析目标，先把这个搞懂，然后分析如何替换图片资源
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.LongType;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.options.Options;

public class Instrumenter {

	private static int tmpCount = 0;
	private static String apkName; // args3
	private static String appFolder;// args4
	private static String pkgName;// args5-----不懂PKG是什么？
	private static String androidJar;// args6
	private static int Prefetch_Method;// args7
	final private static int Prefetch_GETINPUTSTREAM = 0;
	final private static int Prefetch_GETRESPONSECODE = 1;
	final private static int Prefetch_GETCONTENT = 2;
	final private static int Prefetch_OPENSTREAM = 3;
	final private static short PREPROCESS = 0;
	final private static short ALL = 1;
	final private static short ONLYTIMESTAMP = 2;
	final private static short OVERHEAD = 3;
	private static short instrumentOption;
	private static int timestampCounter = 0;
	private static PrintWriter timeStampPrintWriter = null;
	private static PrintWriter urlPrintWriter = null;

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		apkName = args[3];
		appFolder = args[4];
		androidJar = args[5];
		pkgName = args[6];
		instrumentOption = Short.parseShort(args[7]);
		Prefetch_Method = Integer.parseInt(args[8]);
		try {
			File createFile = new File(appFolder + "/Output/timestamp.txt");
			createFile = new File(appFolder + "/Output/url.txt");
			timeStampPrintWriter = new PrintWriter(appFolder
					+ "/Output/timestamp.txt", "UTF-8");
			urlPrintWriter = new PrintWriter(appFolder + "/Output/url.txt",
					"UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		// prefer Android APK files// -src-prec apk
		Options.v().set_src_prec(Options.src_prec_apk);
		// output as APK, too//-f J
		Options.v().set_output_format(Options.output_format_dex);
		if (instrumentOption == ALL) {
			Options.v().set_output_dir(appFolder + "/NewApp");
		} else if (instrumentOption == ONLYTIMESTAMP) {
			Options.v().set_output_dir(appFolder + "/OldAppWithTimestamp");
		} else if (instrumentOption == PREPROCESS) {
			Options.v().set_output_dir(appFolder + "/PreprocessedApp");
		}else if(instrumentOption == OVERHEAD){
			Options.v().set_output_dir(appFolder + "/Overhead");
		}
		Options.v().set_android_jars(androidJar);//查查这个功能
		Options.v().set_whole_program(true);
		Options.v().set_verbose(false);
		Options.v().set_allow_phantom_refs(true);

		// sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator
		// + sootClassPath;
		// Scene.v().setSootClassPath(Scene.v().getSootClassPath());
		Options.v().set_keep_line_number(true);
		Options.v().setPhaseOption("jb", "use-original-names:true");
		// resolve the PrintStream and System soot-classes

		// System.out.println("------------------------java.class.path = "+System.getProperty("java.class.path"));
		Options.v().set_soot_classpath(System.getProperty("java.class.path"));
		Options.v().set_prepend_classpath(true);

		List<String> stringlist = new LinkedList<String>();
		stringlist.add(args[2]);
		Options.v().set_process_dir(stringlist);

		Scene.v().addBasicClass("java.io.PrintStream", SootClass.SIGNATURES);
		Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);

		Scene.v().addBasicClass(ProxyHelper.ProxyClass);//String ProxyClass = "yixue.icse.Proxy";这里是加载了Proxy这个类吗？然后这个类中定义了很多方法
		SootClass scc = Scene.v().loadClassAndSupport(ProxyHelper.ProxyClass);
		scc.setApplicationClass();

		PackManager.v().getPack("jtp")
				.add(new Transform("jtp.myInstrumenter", new BodyTransformer() {

					@Override
					protected void internalTransform(final Body body,
							String phaseName,
							@SuppressWarnings("rawtypes") Map options) {
						//在这里有对后面插桩方法对调用，但是参数body对值从哪里来的？
						if (instrumentOption == ALL) {//可能下面都两个是插桩语句用的
							instrumentURL(body, ProxyHelper.newURLOriginal);//String newURLOriginal = "<java.net.URL: void <init>(java.lang.String)>";
							instrumentAll(body);//这里会跳转到真正的插桩判断函数部分---最重要！！！！！
						} else if (instrumentOption == PREPROCESS) {//下面的插桩都是插入时间戳
							if (Prefetch_Method == Prefetch_GETINPUTSTREAM) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.getInputStreamOriginal);
							} else if (Prefetch_Method == Prefetch_GETRESPONSECODE) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.getResponseCodeOriginal);
							} else if (Prefetch_Method == Prefetch_GETCONTENT) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.getContentOriginal);
							} else if (Prefetch_Method == Prefetch_OPENSTREAM) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.openStreamOriginal);
							}
							instrumentURL(body, ProxyHelper.newURLOriginal);
							// instrumentSendDef(body);
						} else if (instrumentOption == ONLYTIMESTAMP) {
							if (Prefetch_Method == Prefetch_GETINPUTSTREAM) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.getInputStreamOriginal);
							} else if (Prefetch_Method == Prefetch_GETRESPONSECODE) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.getResponseCodeOriginal);
							} else if (Prefetch_Method == Prefetch_GETCONTENT) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.getContentOriginal);
							} else if (Prefetch_Method == Prefetch_OPENSTREAM) {
								instrumentTimestampOnNetwork(body,
										ProxyHelper.openStreamOriginal);
							}
						}else if(instrumentOption == OVERHEAD){
							instrumentTimestampOnSig(body, ProxyHelper.sendDef);
							instrumentTimestampOnSig(body, ProxyHelper.triggerPrefetch);//这里是插桩论文样例程序
						}

						body.validate();//检查插桩是否正确
					}

				}));

		String[] sootArgs = { args[0], args[1], args[2] };
		soot.Main.main(sootArgs);
		timeStampPrintWriter.println("timestamp counter = " + timestampCounter);
		timeStampPrintWriter.close();
		urlPrintWriter.close();
	}

	//下面的代码在例子中的triggerPrefetch()语句的前后插入时间记录语句----------------------------------------------------------------
	private static void instrumentTimestampOnSig(Body body, String sig) {//sig为"void triggerPrefetch(java.lang.String,java...)"
		final PatchingChain<Unit> units = body.getUnits();
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			final Stmt stmt = (Stmt) iter.next();
			if (stmt.containsInvokeExpr()) {
				// System.out.println("invoke stmt = "+stmt);
				InvokeExpr invoke = stmt.getInvokeExpr();
				// if (invoke.getMethod().getSignature().equals(sig))
				if ((invoke.getMethod().getSignature().equals(sig)) ){//这是在插入triggerPrefetch成功后继续插入时间戳语句吗？
					timestampCounter++;

					// SootMethod printTimeStamp = ProxyHelper
					// .findMethod(ProxyHelper.printTimeStamp);
					SootMethod getTimeStamp = ProxyHelper
							.findMethod(ProxyHelper.getTimeStamp);//String getTimeStamp = "long getTimeStamp()"
					SootMethod printTimeDiff = ProxyHelper
							.findMethod(ProxyHelper.printeTimeDiff);//String printeTimeDiff = "void printTimeDiff(java.lang.String,

//					timeStampPrintWriter.println("Stmt: " + stmt);
//					timeStampPrintWriter.println("BodyMethodSig: "
//							+ body.getMethod().getSignature());
//					timeStampPrintWriter.println();

					// Stmt newinvoke = Jimple.v().newInvokeStmt(
					// Jimple.v().newStaticInvokeExpr(
					// printTimeStamp.makeRef(), arglist));
					// Stmt newinvoke2 = Jimple.v().newInvokeStmt(
					// Jimple.v().newStaticInvokeExpr(
					// printTimeStamp.makeRef(), arglist));
					// units.insertBefore(newinvoke, stmt);
					// units.insertAfter(newinvoke2, stmt);

					Stmt getTimeStampInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									getTimeStamp.makeRef()));//新建方法调用语句？
					Local timeStamp1 = addTmpLong2Local(body);//这里新建一个本地变量1
					Stmt assignTimeStampBefore = Jimple.v().newAssignStmt(
							timeStamp1, getTimeStampInvoke.getInvokeExpr());//新建一个赋值语句，将时间赋值给本地变量1
					Local timeStamp2 = addTmpLong2Local(body);//这里新建一个本地变量2
					Stmt assignTimeStampAfter = Jimple.v().newAssignStmt(
							timeStamp2, getTimeStampInvoke.getInvokeExpr());//新建一个赋值语句，将时间赋值给本地变量2
					Local timeDiff = addTmpLong2Local(body);//这里新建一个本地变量diff
					Stmt assignTimeDiff = Jimple.v().newAssignStmt(timeDiff,
							Jimple.v().newSubExpr(timeStamp2, timeStamp1));////新建一个运算操作语句，将时间赋值给本地变量diff

					LinkedList<Value> arglist = new LinkedList<Value>();//新建一个列表用来存储新建的语句
					arglist.add(StringConstant.v(body.getMethod()
							.getSignature()));
					arglist.add(StringConstant.v(stmt.toString()));//感受是在为下面的printTimeDiffInvoke做准备
					arglist.add(timeDiff);
					Stmt printTimeDiffInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									printTimeDiff.makeRef(), arglist));//新建一个信息输出语句

					//下面就在一条一条的塞入语句了
					units.insertBefore(assignTimeStampBefore, stmt);
					units.insertAfter(printTimeDiffInvoke, stmt);
					units.insertAfter(assignTimeDiff, stmt);
					units.insertAfter(assignTimeStampAfter, stmt);
				}
			}
		}
	}
	
	/**
	 * instrument timestamps before "sig" and after "sig" and send timeDiff to
	 * Proxy
	 * 
	 * @param body--这里直接传进去BODY ，即直接进行操作。感觉这里的代码可以直接借用了，不用任何修改，将涉及到到类都添加到新项目里面去
	 * @param sig
	 */
	private static void instrumentTimestampOnNetwork(Body body, String sig) {
		final PatchingChain<Unit> units = body.getUnits();
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			final Stmt stmt = (Stmt) iter.next();
			if (stmt.containsInvokeExpr()) {
				// System.out.println("invoke stmt = "+stmt);
				InvokeExpr invoke = stmt.getInvokeExpr();
				// if (invoke.getMethod().getSignature().equals(sig))
				if ((invoke.getMethod().getSignature().contains("URLConnection")
						|| invoke.getMethod().getSignature()
								.contains("java.io.InputStream")) && !invoke.getMethod().getSignature().contains("read(") ){
					printResponse(body, stmt, invoke, units);//这条语句是干啥的？与我们的需求无关
					timestampCounter++;

					// SootMethod printTimeStamp = ProxyHelper
					// .findMethod(ProxyHelper.printTimeStamp);
					SootMethod getTimeStamp = ProxyHelper
							.findMethod(ProxyHelper.getTimeStamp);//将写好的方法加载并取出
					SootMethod printTimeDiff = ProxyHelper
							.findMethod(ProxyHelper.printeTimeDiff);//将写好的方法加载并取出

					timeStampPrintWriter.println("Stmt: " + stmt);
					timeStampPrintWriter.println("BodyMethodSig: "
							+ body.getMethod().getSignature());
					timeStampPrintWriter.println();

					// Stmt newinvoke = Jimple.v().newInvokeStmt(
					// Jimple.v().newStaticInvokeExpr(
					// printTimeStamp.makeRef(), arglist));
					// Stmt newinvoke2 = Jimple.v().newInvokeStmt(
					// Jimple.v().newStaticInvokeExpr(
					// printTimeStamp.makeRef(), arglist));
					// units.insertBefore(newinvoke, stmt);
					// units.insertAfter(newinvoke2, stmt);

					Stmt getTimeStampInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									getTimeStamp.makeRef()));
					Local timeStamp1 = addTmpLong2Local(body);
					Stmt assignTimeStampBefore = Jimple.v().newAssignStmt(
							timeStamp1, getTimeStampInvoke.getInvokeExpr());
					Local timeStamp2 = addTmpLong2Local(body);
					Stmt assignTimeStampAfter = Jimple.v().newAssignStmt(
							timeStamp2, getTimeStampInvoke.getInvokeExpr());
					Local timeDiff = addTmpLong2Local(body);
					Stmt assignTimeDiff = Jimple.v().newAssignStmt(timeDiff,
							Jimple.v().newSubExpr(timeStamp2, timeStamp1));

					LinkedList<Value> arglist = new LinkedList<Value>();
					arglist.add(StringConstant.v(body.getMethod()
							.getSignature()));
					arglist.add(StringConstant.v(stmt.toString()));
					arglist.add(timeDiff);
					Stmt printTimeDiffInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									printTimeDiff.makeRef(), arglist));

					units.insertBefore(assignTimeStampBefore, stmt);
					units.insertAfter(printTimeDiffInvoke, stmt);
					units.insertAfter(assignTimeDiff, stmt);
					units.insertAfter(assignTimeStampAfter, stmt);
				}
			}
		}
	}

	//print the response of the request
	private static void printResponse(Body body, Stmt stmt, InvokeExpr invoke, PatchingChain<Unit> units){
		if(Prefetch_Method == Prefetch_GETCONTENT){
			
		}else if(Prefetch_Method == Prefetch_GETINPUTSTREAM){
			
		}else if(Prefetch_Method == Prefetch_GETRESPONSECODE){
			System.out.println("stmt = "+stmt +"\n\n\n\n\n");
			if(invoke.getMethod().getSignature().equals(ProxyHelper.getResponseCodeOriginal)){
				System.out.println("sig = "+invoke.getMethod().getSignature());
				if (stmt instanceof AssignStmt) {
					SootMethod printResponseCodeMethod = ProxyHelper
							.findMethod(ProxyHelper.printResponseCode);
					Value value = ((AssignStmt) stmt).getLeftOp();
					LinkedList<Value> arglist = new LinkedList<Value>();
					arglist.add(StringConstant.v(body.getMethod()
							.getSignature()));
					arglist.add(StringConstant.v(stmt.toString()));
					arglist.add(value);
					Stmt printResponseCodeInvk = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									printResponseCodeMethod.makeRef(), arglist));
					System.out.println(printResponseCodeInvk);
					// units.insertBefore(assignIndex, targetAssign);
					// units.insertBefore(assignNodeId, targetAssign);
					units.insertAfter(printResponseCodeInvk, stmt);

				} else {
					System.out.println("getresponse code is not an assignment statement!!!!!!!");

				}
			}
		}else if(Prefetch_Method == Prefetch_OPENSTREAM){
			
		}
	}
	// private static Local addTmpRef(Body body) {
	// Local tmpRef = Jimple.v().newLocal("tmpRef",
	// RefType.v("java.io.PrintStream"));
	// body.getLocals().add(tmpRef);
	// return tmpRef;
	// }
	//
	private static Local addTmpString2Local(Body body) {
		Local tmpString = Jimple.v().newLocal("tmpString" + (tmpCount++),
				RefType.v("java.lang.String"));
		body.getLocals().add(tmpString);//这是添加变量到locals集合，后面会做units里面语句的参数
		return tmpString;
	}

	private static Local addTmpInt2Local(Body body) {
		Local tmpInt = Jimple.v().newLocal("tmpInt" + (tmpCount++),
				RefType.v("java.lang.Integer"));
		body.getLocals().add(tmpInt);
		return tmpInt;
	}

	private static Local addTmpLong2Local(Body body) {
		Local tmpLong = Jimple.v().newLocal("tmpLong" + (tmpCount++),
				LongType.v());
		body.getLocals().add(tmpLong);
		return tmpLong;
	}
//感觉这个很像参数替换
	public static void instrumentWithBody(Body body) {
		// find the body that needs to be instrumented (replace
		// methods)
		if (ProxyHelper.instrumentMap.containsKey(body.getMethod()
				.getSignature())) {
			final PatchingChain<Unit> units = body.getUnits();
			// important to use snapshotIterator here
			for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
				final Stmt stmt = (Stmt) iter.next();
				if (stmt.containsInvokeExpr()) {
					InvokeExpr invoke = stmt.getInvokeExpr();
					// instrumentTimestamp(invoke, stmt, units);
					SootMethod replaceMethod = ProxyHelper
							.queryReplaceMethodWithBody(invoke.getMethod()
									.getSignature());
					// System.out.println("replacement@@@@@@@@@@"+replaceMethod);
					if (replaceMethod != null) {
						// System.out.println("replacement ============ "+replaceMethod.getSignature());
						List<Value> arglist = new LinkedList<Value>();
						for (ValueBox vb : invoke.getUseBoxes()) {
							arglist.add(vb.getValue());
						}
						// Jimple.v().newStaticInvokeExpr(agent.makeRef())
						if (stmt instanceof AssignStmt) {
							Value assivalue = ((AssignStmt) stmt).getLeftOp();
							Stmt newassign = Jimple.v().newAssignStmt(
									assivalue,
									Jimple.v().newStaticInvokeExpr(
											replaceMethod.makeRef(), arglist));
							units.insertBefore(newassign, stmt);
							units.remove(stmt);

						} else if (stmt instanceof InvokeStmt) {
							Stmt newinvoke = Jimple.v().newInvokeStmt(
									Jimple.v().newStaticInvokeExpr(
											replaceMethod.makeRef(), arglist));
							units.insertBefore(newinvoke, stmt);
							units.remove(stmt);

						}
					}
				}
			}
		}
	}

	/**
	 * 
	 * @param body
	 */
	public static void instrumentPrefetchOnLauch(Body body) {
		String bodySig = body.getMethod().getSignature();
		Map<String, String> triggerAtStartMap = ViolistAnalysisHelper
				.getTriggerAtStartMap(appFolder);
		if (triggerAtStartMap.containsKey(bodySig)) {
			final PatchingChain<Unit> units = body.getUnits();
			// important to use snapshotIterator here
			for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
				final Stmt stmt = (Stmt) iter.next();
				System.out.println("@@@@@" + stmt);
				if (stmt.containsInvokeExpr())
				// if(stmt instanceof ReturnStmt
				// || stmt instanceof ReturnVoidStmt)
				{
					InvokeExpr invoke = stmt.getInvokeExpr();
					if (invoke.getMethod().getSignature()
							.contains("void onCreate(android.os.Bundle)")) {
						SootMethod triggerPrefetch = ProxyHelper
								.findMethod(ProxyHelper.triggerPrefetch);
						if (triggerPrefetch == null) {
							System.out
									.println(" @@@@@@@@@@@ triggerPrefetch method is null @@@@@@@@!!!");
						} else {
							LinkedList<Value> arglist = new LinkedList<Value>();
							arglist.add(StringConstant.v(bodySig));
							arglist.add(StringConstant.v(stmt.toString()));
							arglist.add(StringConstant.v(triggerAtStartMap
									.get(bodySig)));
							arglist.add(IntConstant.v(Prefetch_Method));
							Stmt triggerPrefetchInvoke = Jimple.v()
									.newInvokeStmt(
											Jimple.v().newStaticInvokeExpr(
													triggerPrefetch.makeRef(),
													arglist));
							units.insertBefore(triggerPrefetchInvoke, stmt);
						}
					}
				}
			}
		}
	}

//	public static void instrumentPrefetch(Body body) {
//		String bodySig = body.getMethod().getSignature();
//		// for the current body, check if it's in the triggerMethods,
//		// if so, getSigWithId will return the sig with Id and, if not, return
//		// null
//		String triggerMethodWithId = getSigWithId(bodySig,
//				ViolistAnalysisHelper.getTriggerMethods(appFolder));//这里使用到了静态分析，感觉是在静态分析哪里用到了URL解析
//		if (triggerMethodWithId != null) {
//			// nodeIds example: 303@299@307
//			String nodeIds = triggerMethodWithId.replace(bodySig + "@", "");
//			final PatchingChain<Unit> units = body.getUnits();
//			// important to use snapshotIterator here
//			for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
//				final Stmt stmt = (Stmt) iter.next();
//				if (stmt instanceof ReturnStmt
//						|| stmt instanceof ReturnVoidStmt) {
//					SootMethod triggerPrefetch = ProxyHelper
//							.findMethod(ProxyHelper.triggerPrefetch);//这里是找到了事先定义好了的方法？
//					if (triggerPrefetch == null) {
//						System.out
//								.println(" @@@@@@@@@@@ triggerPrefetch method is null @@@@@@@@!!!");
//					} else {
//						LinkedList<Value> arglist = new LinkedList<Value>();//这里就是在重新写语句了
//						arglist.add(StringConstant.v(body.getMethod()
//								.getSignature()));//这里是先加入了一个常量
//						arglist.add(StringConstant.v(stmt.toString()));//加入了一个语句输出信息常量
//						arglist.add(StringConstant.v(nodeIds));//节点ID
//						arglist.add(IntConstant.v(Prefetch_Method));//这里是直接将方法用常量的方式输出？
//						Stmt triggerPrefetchInvoke = Jimple.v().newInvokeStmt(
//								Jimple.v().newStaticInvokeExpr(
//										triggerPrefetch.makeRef(), arglist));//这里是怎么弄的方法调用语句？ARGLIST的都作为参数
//						units.insertBefore(triggerPrefetchInvoke, stmt);
//					}
//				}
//			}
//		}
//	}

	private static String getSigWithId(String bodySig,
			Set<String> triggerMethods) {
		for (String triggerMethod : triggerMethods) {
			if (triggerMethod.contains(bodySig)) {
				return triggerMethod;
			}
		}
		return null;
	}

	public static void instrumentSendDef(Body body) {
		List<DefSpot> defSpotList = DefFinder.getDefSpotListFromTargetField(
				appFolder, apkName, androidJar, pkgName);
//		System.out.println("defSpotList\t" + defSpotList);
		// DefSpot defSpot = defSpotList.get(0);
		for (DefSpot defSpot : defSpotList) {
			if (defSpot.body.equals(body.getMethod().getSignature())) {
				final PatchingChain<Unit> units = body.getUnits();
				// important to use snapshotIterator here
				for (Iterator<Unit> iter = units.snapshotIterator(); iter
						.hasNext();) {
					final Stmt stmt = (Stmt) iter.next();
					if (stmt.toString().equals(defSpot.getJimple())) {
						SootMethod sendDefMethod = ProxyHelper
								.findMethod(ProxyHelper.sendDef);
						if (sendDefMethod == null) {
							System.out
									.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!send def method is null!!!");
						} else {
							if (!(stmt instanceof AssignStmt)) {
								System.out
										.println("@@@@@@@@@@ stmt is not AssignStmt");
							}
							AssignStmt targetAssign = (AssignStmt) stmt;
							Value value = targetAssign.getRightOp();
							// Local index = addTmpInt2Local(body);
							// Local nodeId = addTmpInt2Local(body);
							// AssignStmt assignIndex =
							// Jimple.v().newAssignStmt(index,
							// IntConstant.v(1));
							// AssignStmt assignNodeId =
							// Jimple.v().newAssignStmt(nodeId,
							// IntConstant.v(307));
							LinkedList<Value> arglist = new LinkedList<Value>();
							arglist.add(StringConstant.v(body.getMethod()
									.getSignature()));
							arglist.add(StringConstant.v(stmt.toString()));
							arglist.add(value);
							arglist.add(StringConstant.v(defSpot.getNodeId()));// arglist.add(IntConstant.v(307));
							arglist.add(IntConstant.v(defSpot.getSubStrPos()));//
							arglist.add(StringConstant.v(defSpot.getPkgName()));
							Stmt sendDefInvoke = Jimple.v().newInvokeStmt(
									Jimple.v().newStaticInvokeExpr(
											sendDefMethod.makeRef(), arglist));
							System.out.println(sendDefInvoke);
							// units.insertBefore(assignIndex, targetAssign);
							// units.insertBefore(assignNodeId, targetAssign);
							units.insertBefore(sendDefInvoke, targetAssign);
						}
					}
				}
			}
		}
	}

	public static void instrumentSendManualDef(Body body) {
		List<DefSpot> defSpotList = DefFinder.getDefSpotListFromDefSpots(
				appFolder, pkgName);
//		System.out.println("defSpotList\t" + defSpotList);
		// DefSpot defSpot = defSpotList.get(0);
		for (DefSpot defSpot : defSpotList) {
			if (defSpot.body.equals(body.getMethod().getSignature())) {
				final PatchingChain<Unit> units = body.getUnits();
				// important to use snapshotIterator here
				for (Iterator<Unit> iter = units.snapshotIterator(); iter
						.hasNext();) {
					final Stmt stmt = (Stmt) iter.next();
					if (stmt.toString().equals(defSpot.getJimple())) {
						SootMethod sendDefMethod = ProxyHelper
								.findMethod(ProxyHelper.sendDef);
						if (sendDefMethod == null) {
							System.out
									.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!send def method is null!!!");
						} else {
							if (!(stmt instanceof AssignStmt)) {
								System.out
										.println("@@@@@@@@@@ stmt is not AssignStmt");
							}
							AssignStmt targetAssign = (AssignStmt) stmt;
							Value value = targetAssign.getLeftOp();
							// Local index = addTmpInt2Local(body);
							// Local nodeId = addTmpInt2Local(body);
							// AssignStmt assignIndex =
							// Jimple.v().newAssignStmt(index,
							// IntConstant.v(1));
							// AssignStmt assignNodeId =
							// Jimple.v().newAssignStmt(nodeId,
							// IntConstant.v(307));
							LinkedList<Value> arglist = new LinkedList<Value>();
							arglist.add(StringConstant.v(body.getMethod()
									.getSignature()));
							arglist.add(StringConstant.v(stmt.toString()));
							arglist.add(value);
							arglist.add(StringConstant.v(defSpot.getNodeId()));// arglist.add(IntConstant.v(307));
							arglist.add(IntConstant.v(defSpot.getSubStrPos()));//
							arglist.add(StringConstant.v(defSpot.getPkgName()));
							Stmt sendDefInvoke = Jimple.v().newInvokeStmt(
									Jimple.v().newStaticInvokeExpr(
											sendDefMethod.makeRef(), arglist));
							System.out.println(sendDefInvoke);
							// units.insertBefore(assignIndex, targetAssign);
							// units.insertBefore(assignNodeId, targetAssign);
							units.insertAfter(sendDefInvoke, targetAssign);
//							return;
						}
					}
				}
			}
		}
	}
	
	public static void instrumentSendManualDefForJson(Body body) {
		List<DefSpot> defSpotList = DefFinder.getDefSpotListFromDefSpots(
				appFolder, pkgName);
//		System.out.println("defSpotList\t" + defSpotList);
		// DefSpot defSpot = defSpotList.get(0);
		for (DefSpot defSpot : defSpotList) {
			if (defSpot.body.equals(body.getMethod().getSignature())) {
				final PatchingChain<Unit> units = body.getUnits();
				// important to use snapshotIterator here
				for (Iterator<Unit> iter = units.snapshotIterator(); iter
						.hasNext();) {
					final Stmt stmt = (Stmt) iter.next();
					if (stmt.toString().equals(defSpot.getJimple())) {
						SootMethod sendDefMethod = ProxyHelper
								.findMethod(ProxyHelper.sendDef);
						if (sendDefMethod == null) {
							System.out
									.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!send def method is null!!!");
						} else {
							if (!(stmt instanceof AssignStmt)) {
								System.out
										.println("@@@@@@@@@@ stmt is not AssignStmt");
							}
							if (stmt instanceof AssignStmt) {
								AssignStmt targetAssign = (AssignStmt) stmt;
								Value value = targetAssign.getRightOp();
								// Local index = addTmpInt2Local(body);
								// Local nodeId = addTmpInt2Local(body);
								// AssignStmt assignIndex =
								// Jimple.v().newAssignStmt(index,
								// IntConstant.v(1));
								// AssignStmt assignNodeId =
								// Jimple.v().newAssignStmt(nodeId,
								// IntConstant.v(307));
								LinkedList<Value> arglist = new LinkedList<Value>();
								arglist.add(StringConstant.v(body.getMethod()
										.getSignature()));
								arglist.add(StringConstant.v(stmt.toString()));
								arglist.add(value);
								arglist.add(StringConstant.v(defSpot
										.getNodeId()));// arglist.add(IntConstant.v(307));
								arglist.add(IntConstant.v(defSpot
										.getSubStrPos()));//
								arglist.add(StringConstant.v(defSpot
										.getPkgName()));
								Stmt sendDefInvoke = Jimple.v().newInvokeStmt(
										Jimple.v().newStaticInvokeExpr(
												sendDefMethod.makeRef(),
												arglist));
								System.out.println(sendDefInvoke);
								// units.insertBefore(assignIndex,
								// targetAssign);
								// units.insertBefore(assignNodeId,
								// targetAssign);
								units.insertBefore(sendDefInvoke, targetAssign);
							} else if (stmt instanceof InvokeStmt) {
								InvokeExpr invoke = stmt.getInvokeExpr();
								Value jsonObjValue = invoke.getArg(0);
								SootClass BT_strings = Scene.v()
										.loadClassAndSupport(
												"com.thepuzzle.BT_strings");
								SootMethod getJsonMethod = null;
								for (SootMethod method : BT_strings
										.getMethods()) {
									if (method
											.getSignature()
											.equals("<com.thepuzzle.BT_strings: java.lang.String getJsonPropertyValue(org.json.JSONObject,java.lang.String,java.lang.String)>")) {
										getJsonMethod = method;
									}
								}

								LinkedList<Value> arglistOfgetJson = new LinkedList<Value>();
								arglistOfgetJson.add(jsonObjValue);
								arglistOfgetJson.add(StringConstant
										.v("dataURL"));
								arglistOfgetJson.add(StringConstant.v(""));
								Stmt getJsonInvoke = Jimple.v().newInvokeStmt(
										Jimple.v().newStaticInvokeExpr(
												getJsonMethod.makeRef(),
												arglistOfgetJson));
								Local urlData = addTmpString2Local(body);
								Stmt assignUrlData = Jimple.v().newAssignStmt(
										urlData, getJsonInvoke.getInvokeExpr());

								LinkedList<Value> arglistOfSendDef = new LinkedList<Value>();
								arglistOfSendDef.add(StringConstant.v(body
										.getMethod().getSignature()));
								arglistOfSendDef.add(StringConstant.v(stmt
										.toString()));
								arglistOfSendDef.add(urlData);
								arglistOfSendDef.add(StringConstant.v(defSpot
										.getNodeId()));// arglist.add(IntConstant.v(307));
								arglistOfSendDef.add(IntConstant.v(defSpot
										.getSubStrPos()));//
								arglistOfSendDef.add(StringConstant.v(defSpot
										.getPkgName()));
								Stmt sendDefInvoke = Jimple.v().newInvokeStmt(
										Jimple.v().newStaticInvokeExpr(
												sendDefMethod.makeRef(),
												arglistOfSendDef));
								System.out.println(sendDefInvoke);

								units.insertBefore(assignUrlData, stmt);
								units.insertBefore(sendDefInvoke, stmt);

							}

							return;
						}
					}
				}
			}
		}
	}

	/***
	 * instrument after sig, to print out the string value.
	 * 
	 * @param body
	 * @param sig
	 *            is new URL(string)
	 */
	private static void instrumentURL(Body body, String sig) {//参数sig是一个url字符串
		final PatchingChain<Unit> units = body.getUnits();
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			final Stmt stmt = (Stmt) iter.next();
			if (stmt.containsInvokeExpr()) {
				// System.out.println("invoke stmt = "+stmt);
				InvokeExpr invoke = stmt.getInvokeExpr();
				if (invoke.getMethod().getSignature().equals(sig)) {//这里是什么意思？不应该是和sig比较啊

					SootMethod printUrl = ProxyHelper
							.findMethod(ProxyHelper.printUrl);//提取方法String printUrl = "void printUrl(java.lang.String,java.lang.String,

					urlPrintWriter.println("Stmt: " + stmt);//这里是调用io的库方法输出
					urlPrintWriter.println("BodyMethodSig: "
							+ body.getMethod().getSignature());
					urlPrintWriter.println();

					LinkedList<Value> arglist = new LinkedList<Value>();
					arglist.add(StringConstant.v(body.getMethod()
							.getSignature()));
					arglist.add(StringConstant.v(stmt.toString()));
					arglist.add(invoke.getArg(0));

					Stmt printUrlInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(printUrl.makeRef(),
									arglist));

					units.insertAfter(printUrlInvoke, stmt);
					
//					fetchFurther(invoke.getArg(0), body, stmt, units);
				}
			}
		}
	}

	public static void fetchFurther(Value url, Body body, Stmt stmt, PatchingChain<Unit> units){
//		 if(url.toString().contains("http://www.hablema.com/greyhatmoders/appl/wallpaper/angel/thumb/"))
		 {
			 SootMethod returnBigUrl = ProxyHelper.findMethod(ProxyHelper.returnBigUrl);
			 
			 SootMethod sendDefMethod = ProxyHelper
						.findMethod(ProxyHelper.sendDef);
			 LinkedList<Value> arglistOfBigUrl = new LinkedList<Value>();
			 arglistOfBigUrl.add(url);
			 Stmt bigUrlInvoke = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(returnBigUrl.makeRef(), arglistOfBigUrl));
			 Local urlLocal = addTmpString2Local(body);
				Stmt assignUrlStmt = Jimple.v().newAssignStmt(
						urlLocal, bigUrlInvoke.getInvokeExpr());
			 
					LinkedList<Value> arglistOfSendDef = new LinkedList<Value>();
					arglistOfSendDef.add(StringConstant.v(body.getMethod()
							.getSignature()));
					arglistOfSendDef.add(StringConstant.v(stmt.toString()));
					arglistOfSendDef.add(urlLocal);
					arglistOfSendDef.add(StringConstant.v("381"));// arglist.add(IntConstant.v(307));
					arglistOfSendDef.add(IntConstant.v(0));//
					arglistOfSendDef.add(StringConstant.v("com.love.pink"));
					Stmt sendDefInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									sendDefMethod.makeRef(), arglistOfSendDef));
					System.out.println(sendDefInvoke);
					// units.insertBefore(assignIndex, targetAssign);
					// units.insertBefore(assignNodeId, targetAssign);
					units.insertAfter(sendDefInvoke, stmt);
					units.insertAfter(assignUrlStmt, stmt);
		 }
	}
	/**
	 * this method will do the following
	 * 
	 * add timestamp before and after each Prefetch_Method, e.g.,
	 * getInputStream(); replace all the Prefetch_Method, e.g.,
	 * getInputStream(); insert sendDef(...) ; insert triggerPrefetch(...)
	 * before each return statement in each trigger method;
	 * 
	 * @param body
	 */
	public static void instrumentAll(Body body) {
		if (Prefetch_Method == Prefetch_GETINPUTSTREAM) {//这里的四个也是加时间戳
			instrumentTimestampOnNetwork(body, ProxyHelper.getInputStreamOriginal);
		} else if (Prefetch_Method == Prefetch_GETRESPONSECODE) {
			instrumentTimestampOnNetwork(body, ProxyHelper.getResponseCodeOriginal);
		} else if (Prefetch_Method == Prefetch_GETCONTENT) {
			instrumentTimestampOnNetwork(body, ProxyHelper.getContentOriginal);
		} else if (Prefetch_Method == Prefetch_OPENSTREAM) {
			instrumentTimestampOnNetwork(body, ProxyHelper.openStreamOriginal);
		}
		instrumentSendDef(body);
//		 instrumentGetURLfromJsonParent(body);//--------这个也重要
//		 instrumentSendManualDef(body);
		//instrumentPrefetch(body);//---------------最最重要的代码部分，似乎是如何写一个方法调用语句
//		 instrumentPrefetchOnLauch(body);
		if (Prefetch_Method == Prefetch_GETINPUTSTREAM) {
			replaceMethod(body, ProxyHelper.getInputStreamOriginal);//-------也要看看这个
		} else if (Prefetch_Method == Prefetch_GETRESPONSECODE) {
			replaceMethod(body, ProxyHelper.getResponseCodeOriginal);
		} else if (Prefetch_Method == Prefetch_GETCONTENT) {
			replaceMethod(body, ProxyHelper.getContentOriginal);
		} else if (Prefetch_Method == Prefetch_OPENSTREAM) {
			replaceMethod(body, ProxyHelper.openStreamOriginal);
		}
		// replace all
		else {
			replaceMethod(body, ProxyHelper.getInputStreamOriginal);
			replaceMethod(body, ProxyHelper.getResponseCodeOriginal);
			replaceMethod(body, ProxyHelper.getContentOriginal);
		}
		// if(body.getMethod().getSignature().contains("com.lighthouse1.mobilebenefits.activity.Login")){
		// instrumentTimeDiffForInvoke(body);
		// }
	}

	public static void replaceMethod(Body body, String originalSig) {
		final PatchingChain<Unit> units = body.getUnits();
		// important to use snapshotIterator here
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			final Stmt stmt = (Stmt) iter.next();
			if (stmt.containsInvokeExpr()) {
				InvokeExpr invoke = stmt.getInvokeExpr();
				SootMethod replaceMethod = null;
				if (invoke.getMethod().getSignature().equals(originalSig)) {//根据不同的条件，判断提供什么样的替换方法类型
					if (Prefetch_Method == Prefetch_GETRESPONSECODE) {
						replaceMethod = ProxyHelper
								.findMethod(ProxyHelper.getResponseCodeNew);//getResponseCodeNew = "int getResponseCode(java.net.HttpURLConnection)";
					} else if (Prefetch_Method == Prefetch_GETINPUTSTREAM) {
						replaceMethod = ProxyHelper
								.findMethod(ProxyHelper.getInputStreamNew);
					} else if (Prefetch_Method == Prefetch_GETCONTENT) {
						replaceMethod = ProxyHelper
								.findMethod(ProxyHelper.getContentNew);//getContentNew = "java.lang.Object getContent(java.net.URLConnection)";
					} else if (Prefetch_Method == Prefetch_OPENSTREAM) {
						replaceMethod = ProxyHelper
								.findMethod(ProxyHelper.openStreamNew);
					}
					if (replaceMethod != null) {
						System.out.println("replacement:\n "
								+ replaceMethod.getSignature());
						List<Value> arglist = new LinkedList<Value>();
						for (ValueBox vb : invoke.getUseBoxes()) {//这里的valueBox是什么？感觉是参数名的具体字符串，很可能
							arglist.add(vb.getValue());//这里应该是获得调用语句中对参数吧，其实可以在这个过程中对参数值进行替换的
						}
						// Jimple.v().newStaticInvokeExpr(agent.makeRef())
						if (stmt instanceof AssignStmt) {//如果语句是赋值语句
							Value assivalue = ((AssignStmt) stmt).getLeftOp();//获得左值
							Stmt newassign = Jimple.v().newAssignStmt(
									assivalue,
									Jimple.v().newStaticInvokeExpr(
											replaceMethod.makeRef(), arglist));//这里是对左值进行重新赋值
							units.insertBefore(newassign, stmt);//插入新对赋值语句，删除旧对赋值语句
							units.remove(stmt);

						} else if (stmt instanceof InvokeStmt) {//如果这个语句是方法调用语句
							Stmt newinvoke = Jimple.v().newInvokeStmt(
									Jimple.v().newStaticInvokeExpr(
											replaceMethod.makeRef(), arglist));//直接新建一条语句
							units.insertBefore(newinvoke, stmt);//插入
							units.remove(stmt);//删除旧语句

						}
					}
				}
			}
		}
	}

	public static void instrumentTimeDiffForInvoke(Body body) {
		final PatchingChain<Unit> units = body.getUnits();
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			final Stmt stmt = (Stmt) iter.next();
			if (stmt.containsInvokeExpr()) {
				// System.out.println("invoke stmt = "+stmt);
				// InvokeExpr invoke = stmt.getInvokeExpr();
				// if (invoke.getMethod().getSignature().equals(sig))
				// if
				// (invoke.getMethod().getSignature().contains("URLConnection"))
				{

					// SootMethod printTimeStamp = ProxyHelper
					// .findMethod(ProxyHelper.printTimeStamp);
					SootMethod getTimeStamp = ProxyHelper
							.findMethod(ProxyHelper.getTimeStamp);
					SootMethod printTimeDiff = ProxyHelper
							.findMethod(ProxyHelper.printeTimeDiff);

					Stmt getTimeStampInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									getTimeStamp.makeRef()));
					Local timeStamp1 = addTmpLong2Local(body);
					Stmt assignTimeStampBefore = Jimple.v().newAssignStmt(
							timeStamp1, getTimeStampInvoke.getInvokeExpr());
					Local timeStamp2 = addTmpLong2Local(body);
					Stmt assignTimeStampAfter = Jimple.v().newAssignStmt(
							timeStamp2, getTimeStampInvoke.getInvokeExpr());
					Local timeDiff = addTmpLong2Local(body);
					Stmt assignTimeDiff = Jimple.v().newAssignStmt(timeDiff,
							Jimple.v().newSubExpr(timeStamp2, timeStamp1));

					LinkedList<Value> arglist = new LinkedList<Value>();
					arglist.add(StringConstant.v(body.getMethod()
							.getSignature()));
					arglist.add(StringConstant.v(stmt.toString()));
					arglist.add(timeDiff);
					Stmt printTimeDiffInvoke = Jimple.v().newInvokeStmt(
							Jimple.v().newStaticInvokeExpr(
									printTimeDiff.makeRef(), arglist));

					units.insertBefore(assignTimeStampBefore, stmt);
					units.insertAfter(printTimeDiffInvoke, stmt);
					units.insertAfter(assignTimeDiff, stmt);
					units.insertAfter(assignTimeStampAfter, stmt);
				}
			}
		}
	}
//这个应该是修改参数的那个吧！！！----------------------------------------------------------------------------
	public static void instrumentGetURLfromJson(Body body) {

		final PatchingChain<Unit> units = body.getUnits();
		// important to use snapshotIterator here
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			final Stmt stmt = (Stmt) iter.next();//得到来每一条语句
			SootMethod sendDefMethod = ProxyHelper
					.findMethod(ProxyHelper.sendDef);
			if (sendDefMethod == null) {
				System.out
						.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!send def method is null!!!");
			} else {
				if (!(stmt instanceof AssignStmt)) {
					System.out.println("@@@@@@@@@@ stmt is not AssignStmt");
				}
				if (stmt instanceof InvokeStmt) {
					InvokeExpr invoke = stmt.getInvokeExpr();
					if (invoke.getMethod().getSignature()
							.contains("setJsonObject(org.json.JSONObject)")) {
						Value jsonObjValue = invoke.getArg(0);
						SootMethod getURLfromJson = ProxyHelper
								.findMethod(ProxyHelper.getURLfromJson);
						Stmt getURLfromJsonInvoke = Jimple.v().newInvokeStmt(
								Jimple.v().newStaticInvokeExpr(
										getURLfromJson.makeRef()));
						Local urlData = addTmpString2Local(body);
						Stmt assignUrlData = Jimple.v().newAssignStmt(urlData,
								getURLfromJsonInvoke.getInvokeExpr());

						LinkedList<Value> arglistOfSendDef = new LinkedList<Value>();
						arglistOfSendDef.add(StringConstant.v(body.getMethod()
								.getSignature()));
						arglistOfSendDef.add(StringConstant.v(stmt.toString()));
						arglistOfSendDef.add(urlData);
						arglistOfSendDef.add(StringConstant.v("17771"));
						arglistOfSendDef.add(IntConstant.v(0));//
						arglistOfSendDef.add(StringConstant.v("com.thepuzzle"));
						Stmt sendDefInvoke = Jimple.v().newInvokeStmt(
								Jimple.v().newStaticInvokeExpr(
										sendDefMethod.makeRef(),
										arglistOfSendDef));
						System.out.println(sendDefInvoke);

						units.insertBefore(assignUrlData, stmt);
						units.insertBefore(sendDefInvoke, stmt);
					}

				}

				return;
			}

		}

	}

	public static void instrumentGetURLfromJsonParent(Body body) {
		if (body.getMethod()
				.getSignature()
				.equals(
						"<com.thepuzzle.BT_screen_menuListSimple$ChildItemAdapter: android.view.View getView(int,android.view.View,android.view.ViewGroup)>")) {
			final PatchingChain<Unit> units = body.getUnits();
			// important to use snapshotIterator here
			for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
				final Stmt stmt = (Stmt) iter.next();
				if (stmt.containsInvokeExpr()
						&& stmt.getInvokeExpr()
								.getMethod()
								.getSignature()
								.equals("<com.thepuzzle.BT_strings: java.lang.String getJsonPropertyValue(org.json.JSONObject,java.lang.String,java.lang.String)>")) {
					SootMethod sendDefMethod = ProxyHelper
							.findMethod(ProxyHelper.sendDef);
					if (sendDefMethod == null) {
						System.out
								.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!send def method is null!!!");
					} else {
						InvokeExpr invoke = stmt.getInvokeExpr();
							Value jsonObjValue = invoke.getArg(0);
							LinkedList<Value> arglistOfGetURL = new LinkedList<Value>();
							arglistOfGetURL.add(jsonObjValue);
							SootMethod getURLfromJsonParent = ProxyHelper
									.findMethod(ProxyHelper.getURLfromJsonParent);
							Stmt getURLfromJsonInvoke = Jimple.v()
									.newInvokeStmt(
											Jimple.v().newStaticInvokeExpr(
													getURLfromJsonParent.makeRef(), arglistOfGetURL));
							Local urlData = addTmpString2Local(body);
							Stmt assignUrlData = Jimple.v().newAssignStmt(
									urlData,
									getURLfromJsonInvoke.getInvokeExpr());

							LinkedList<Value> arglistOfSendDef = new LinkedList<Value>();
							arglistOfSendDef.add(StringConstant.v(body
									.getMethod().getSignature()));
							arglistOfSendDef.add(StringConstant.v(stmt
									.toString()));
							arglistOfSendDef.add(urlData);
							arglistOfSendDef.add(StringConstant.v("17771"));
							arglistOfSendDef.add(IntConstant.v(0));//
							arglistOfSendDef.add(StringConstant
									.v("com.thepuzzle"));
							Stmt sendDefInvoke = Jimple.v().newInvokeStmt(
									Jimple.v().newStaticInvokeExpr(
											sendDefMethod.makeRef(),
											arglistOfSendDef));
							System.out.println(sendDefInvoke);

							units.insertBefore(assignUrlData, stmt);
							units.insertBefore(sendDefInvoke, stmt);
							return;
						}

					}
				}

		}

	}

	/**
	 * replace all the methods according to the jimpleReplaceMap defined in
	 * ProxyHelper
	 * 
	 * @param body
	 */
	public static void replaceMethod(Body body) {
		PatchingChain<Unit> units = body.getUnits();
		// important to use snapshotIterator here
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			final Stmt stmt = (Stmt) iter.next();
			if (stmt.containsInvokeExpr()) {
				InvokeExpr invoke = stmt.getInvokeExpr();
				SootMethod replaceMethod = ProxyHelper
						.queryReplaceMethod(invoke.getMethod().getSignature());
				// System.out.println("replacement@@@@@@@@@@" + replaceMethod);
				if (replaceMethod != null) {
					System.out.println("replacement:\n "
							+ replaceMethod.getSignature());
					List<Value> arglist = new LinkedList<Value>();
					for (ValueBox vb : invoke.getUseBoxes()) {
						arglist.add(vb.getValue());
					}
					// Jimple.v().newStaticInvokeExpr(agent.makeRef())
					if (stmt instanceof AssignStmt) {
						Value assivalue = ((AssignStmt) stmt).getLeftOp();
						Stmt newassign = Jimple.v().newAssignStmt(
								assivalue,
								Jimple.v().newStaticInvokeExpr(
										replaceMethod.makeRef(), arglist));
						units.insertBefore(newassign, stmt);
						units.remove(stmt);

					} else if (stmt instanceof InvokeStmt) {
						Stmt newinvoke = Jimple.v().newInvokeStmt(
								Jimple.v().newStaticInvokeExpr(
										replaceMethod.makeRef(), arglist));
						units.insertBefore(newinvoke, stmt);
						units.remove(stmt);

					}
				}
			}
		}
	}
}
