package com.verivital.hyst.generators;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

import com.verivital.hyst.geometry.Interval;
import com.verivital.hyst.grammar.formula.Constant;
import com.verivital.hyst.grammar.formula.Expression;
import com.verivital.hyst.grammar.formula.FormulaParser;
import com.verivital.hyst.grammar.formula.Operation;
import com.verivital.hyst.grammar.formula.Operator;
import com.verivital.hyst.grammar.formula.Variable;
import com.verivital.hyst.ir.AutomatonExportException;
import com.verivital.hyst.ir.Configuration;
import com.verivital.hyst.ir.base.AutomatonMode;
import com.verivital.hyst.ir.base.AutomatonTransition;
import com.verivital.hyst.ir.base.BaseComponent;
import com.verivital.hyst.ir.base.ExpressionInterval;
import com.verivital.hyst.util.DoubleArrayOptionHandler;

/**
 * Creates the NAV benchmark, from "Benchmarks for Hybrid Systems Verification",
 * Fehnker et. al, HSCC 2004
 * 
 * 
 * @author Stanley Bak (May 2016)
 *
 */
public class NavigationGenerator extends ModelGenerator
{
	@Option(name = "-matrix", required = true, usage = "space-sepeated values of four-element matrix A, like '-1.2 0.1 0.1 -1.2'", handler = DoubleArrayOptionHandler.class, metaVar = "a11 a12 a21 a22")
	private List<Double> matrixString;
	private double[][] matrixA = { { 0, 0 }, { 0, 0 } };

	@Option(name = "-prefix", usage = "mode name prefix", metaVar = "NAME")
	private String modePrefix = "mode_";

	@Option(name = "-i_list", required = true, usage = "space-separated list of target velocity i value for each mode, each i is 0-8 or 'A' or 'B'", handler = StringArrayOptionHandler.class, metaVar = "i1 i2 ...")
	private List<String> iList = null;
	private List<Integer> iListProcessed = null; // uses 9 for 'A' and 10 for
													// 'B'

	@Option(name = "-width", required = true, usage = "width of the grid (# of modes)", metaVar = "WIDTH")
	private int width = 1;

	private int height = -1; // derived from list size and width

	@Option(name = "-startx", required = true, usage = "x start position", metaVar = "REAL")
	private double xInit = 0.0;

	@Option(name = "-starty", required = true, usage = "y start position", metaVar = "REAL")
	private double yInit = 0.0;

	String startModeName = null;

	@Option(name = "-noise", usage = "amount of input noise [-val,val] to add to xvel and yel", metaVar = "VAL")
	private double noise = 0.0;

	private void checkParams()
	{
		if (noise < 0)
			throw new AutomatonExportException("Noise should be nonnegative.");

		if (matrixString.size() != 4)
			throw new AutomatonExportException("Matrix A should have exactly four elements.");

		matrixA[0][0] = matrixString.get(0);
		matrixA[0][1] = matrixString.get(1);
		matrixA[1][0] = matrixString.get(2);
		matrixA[1][1] = matrixString.get(3);

		iListProcessed = new ArrayList<Integer>();

		try
		{
			for (String s : iList)
			{
				if (s.equals("A") || s.equals("a"))
					iListProcessed.add(9);
				else if (s.equals("B") || s.equals("b"))
					iListProcessed.add(10);
				else
				{
					int i = Integer.parseInt(s);

					if (i < 0 || i > 8)
						throw new AutomatonExportException(
								"i_list argument was invalid: '" + i + "'");

					iListProcessed.add(i);
				}
			}
		}
		catch (NumberFormatException e)
		{
			throw new AutomatonExportException(
					"Error parsing i_list argument as integer " + e.toString(), e);
		}

		if (width <= 0)
			throw new AutomatonExportException("Width should be positive.");

		if (iListProcessed.size() % width != 0)
			throw new AutomatonExportException(
					"Width(" + width + ") should evenly divide number of elements in i_list ("
							+ iListProcessed.size() + ").");

		int startX = (int) Math.floor(xInit);
		int startY = (int) Math.floor(yInit);

		height = iListProcessed.size() / width;

		if (startX < 0)
			startX = 0;
		else if (startX >= width)
			startX = width - 1;

		if (startY < 0)
			startY = 0;
		else if (startY >= height)
			startY = height - 1;

		startModeName = modePrefix + startX + "_" + startY;
	}

	@Override
	public String getCommandLineFlag()
	{
		return "nav";
	}

	@Override
	public String getName()
	{
		return "Navigation [Fehnker06]";
	}

	@Override
	protected Configuration generateModel()
	{
		checkParams();

		BaseComponent ha = new BaseComponent();
		Configuration c = new Configuration(ha);

		ha.variables.add("x");
		ha.variables.add("y");
		ha.variables.add("xvel");
		ha.variables.add("yvel");

		AutomatonMode[][] modes = new AutomatonMode[height][width];

		makeModes(c, ha, modes);

		makeTransitions(c, ha, modes);

		// initial states
		Expression initExp = FormulaParser.parseInitialForbidden(
				"x == " + xInit + " && y == " + yInit + " & -1 <= xvel <= 1 & -1 <= yvel <= 1");
		c.init.put(startModeName, initExp);

		// assign plot variables
		c.settings.plotVariableNames[0] = "x";
		c.settings.plotVariableNames[1] = "y";

		return c;
	}

	/**
	 * Make transitions and guards between modes
	 * 
	 * @param modes
	 */
	private void makeTransitions(Configuration c, BaseComponent ha, AutomatonMode[][] modes)
	{
		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				AutomatonMode am = modes[y][x];

				if (x != 0)
				{
					AutomatonTransition at = ha.createTransition(am, modes[y][x - 1]);
					at.guard = new Operation("x", Operator.LESSEQUAL, x);
				}

				if (x != width - 1)
				{
					AutomatonTransition at = ha.createTransition(am, modes[y][x + 1]);
					at.guard = new Operation("x", Operator.GREATEREQUAL, x + 1);
				}

				if (y != 0)
				{
					AutomatonTransition at = ha.createTransition(am, modes[y - 1][x]);
					at.guard = new Operation("y", Operator.LESSEQUAL, y);
				}

				if (y != height - 1)
				{
					AutomatonTransition at = ha.createTransition(am, modes[y + 1][x]);

					at.guard = new Operation("y", Operator.GREATEREQUAL, y + 1);
				}
			}
		}
	}

	/**
	 * Make mode objects, flows, and invariants
	 * 
	 * @param modes
	 */
	private void makeModes(Configuration c, BaseComponent ha, AutomatonMode[][] modes)
	{
		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				String name = modePrefix + x + "_" + y;
				AutomatonMode am = modes[y][x] = ha.createMode(name);

				int i = iListProcessed.get(x + y * width);

				if (i == 10) // 'B'
					c.forbidden.put(name, Constant.TRUE);

				assignDerivative(am, i);
				addInvariant(am, x, y);
			}
		}
	}

	private void addInvariant(AutomatonMode am, int x, int y)
	{
		Expression inv = Constant.TRUE;

		if (x != 0)
			inv = Expression.and(inv, new Operation("x", Operator.GREATEREQUAL, x));

		if (x != width - 1)
			inv = Expression.and(inv, new Operation("x", Operator.LESSEQUAL, x + 1));

		if (y != 0)
			inv = Expression.and(inv, new Operation("y", Operator.GREATEREQUAL, y));

		if (y != height - 1)
			inv = Expression.and(inv, new Operation("y", Operator.LESSEQUAL, y + 1));

		am.invariant = inv;
	}

	private void assignDerivative(AutomatonMode am, int i)
	{
		if (i > 8)
		{
			am.flowDynamics.put("x", new ExpressionInterval(new Constant(0)));
			am.flowDynamics.put("y", new ExpressionInterval(new Constant(0)));
			am.flowDynamics.put("xvel", new ExpressionInterval(new Constant(0)));
			am.flowDynamics.put("yvel", new ExpressionInterval(new Constant(0)));
		}
		else
		{
			am.flowDynamics.put("x", new ExpressionInterval(new Variable("xvel")));
			am.flowDynamics.put("y", new ExpressionInterval(new Variable("yvel")));

			double desiredXvel = Math.sin(i * Math.PI / 4);
			double desiredYvel = Math.cos(i * Math.PI / 4);

			// v' = A(v-vd)
			Expression xvelDer = FormulaParser.parseValue(matrixA[0][0] + " * (xvel - "
					+ desiredXvel + ") + " + matrixA[0][1] + " * (yvel - " + desiredYvel + ")");
			Expression yvelDer = FormulaParser.parseValue(matrixA[1][0] + " * (xvel - "
					+ desiredXvel + ") + " + matrixA[1][1] + " * (yvel - " + desiredYvel + ")");

			Interval uncertainty = null;

			if (noise > 0)
				uncertainty = new Interval(-noise, noise);

			am.flowDynamics.put("xvel", new ExpressionInterval(xvelDer, uncertainty));
			am.flowDynamics.put("yvel", new ExpressionInterval(yvelDer, uncertainty));
		}
	}
}
