/*
 * Copyright 2012 Google Inc.
 * Copyright 2012 Matt Corallo.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.core;

import com.google.common.collect.Lists;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.utils.BlockFileLoader;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import static org.bitcoinj.core.Coin.FIFTY_COINS;
import static org.junit.Assert.*;
import org.junit.rules.ExpectedException;

/**
 * We don't do any wallet tests here, we leave that to {@link ChainSplitTest}
 */

public abstract class AbstractFullPrunedBlockChainTest {
    @org.junit.Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final Logger log = LoggerFactory.getLogger(AbstractFullPrunedBlockChainTest.class);

    protected static final NetworkParameters PARAMS = new UnitTestParams() {
        @Override public int getInterval() {
            return 10000;
        }
    };
    protected FullPrunedBlockChain chain;
    protected FullPrunedBlockStore store;

    @Before
    public void setUp() throws Exception {
        BriefLogFormatter.init();
        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
    }

    public abstract FullPrunedBlockStore createStore(NetworkParameters params, int blockCount)
        throws BlockStoreException;

    public abstract void resetStore(FullPrunedBlockStore store) throws BlockStoreException;

    @Test
    public void testGeneratedChain() throws Exception {
        // TODO
    }

    @Test
    public void skipScripts() throws Exception {
        store = createStore(PARAMS, 10);
        chain = new FullPrunedBlockChain(PARAMS, store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)

        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = PARAMS.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++);
        chain.add(rollingBlock);
        TransactionOutput spendableOutput = rollingBlock.getTransactions().get(0).getOutput(0);
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++);
            chain.add(rollingBlock);
        }

        rollingBlock = rollingBlock.createNextBlock(null);
        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, FIFTY_COINS, new byte[] {}));
        TransactionInput input = t.addInput(spendableOutput);
        // Invalid script.
        input.clearScriptBytes();
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        chain.setRunScripts(false);
        try {
            chain.add(rollingBlock);
        } catch (VerificationException e) {
            fail();
        }
        try {
            store.close();
        } catch (Exception e) {}
    }

    @Test
    public void testFinalizedBlocks() throws Exception {
        // TODO
    }
    
    @Test
    public void testFirst100KBlocks() throws Exception {
        NetworkParameters params = MainNetParams.get();
        Context context = new Context(params);
        File blockFile = new File(getClass().getResource("first-100k-blocks.dat").getFile());
        BlockFileLoader loader = new BlockFileLoader(params, Arrays.asList(blockFile));
        
        store = createStore(params, 10);
        resetStore(store);
        chain = new FullPrunedBlockChain(context, store);
        for (Block block : loader)
            chain.add(block);
        try {
            store.close();
        } catch (Exception e) {}
    }

    @Test
    public void testGetOpenTransactionOutputs() throws Exception {
        // TODO
    }

    @Test
    public void testUTXOProviderWithWallet() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        chain = new FullPrunedBlockChain(PARAMS, store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output.
        Block rollingBlock = PARAMS.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++);
        chain.add(rollingBlock);
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++);
            chain.add(rollingBlock);
        }
        rollingBlock = rollingBlock.createNextBlock(null);

        // Create 1 BTC spend to a key in this wallet (to ourselves).
        Wallet wallet = new Wallet(PARAMS);
        assertEquals("Available balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        assertEquals("Estimated balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.ESTIMATED));

        wallet.setUTXOProvider(store);
        ECKey toKey = wallet.freshReceiveKey();
        Coin amount = Coin.valueOf(100000000);

        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        chain.add(rollingBlock);

        // Create another spend of 1/2 the value of BTC we have available using the wallet (store coin selector).
        ECKey toKey2 = new ECKey();
        Coin amount2 = amount.divide(2);
        Address address2 = new Address(PARAMS, toKey2.getPubKeyHash());
        SendRequest req = SendRequest.to(address2, amount2);
        wallet.completeTx(req);
        wallet.commitTx(req.tx);
        Coin fee = Coin.ZERO;

        // There should be one pending tx (our spend).
        assertEquals("Wrong number of PENDING.4", 1, wallet.getPoolSize(WalletTransaction.Pool.PENDING));
        Coin totalPendingTxAmount = Coin.ZERO;
        for (Transaction tx : wallet.getPendingTransactions()) {
            totalPendingTxAmount = totalPendingTxAmount.add(tx.getValueSentToMe(wallet));
        }

        // The availbale balance should be the 0 (as we spent the 1 BTC that's pending) and estimated should be 1/2 - fee BTC
        assertEquals("Available balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        assertEquals("Estimated balance is incorrect", amount2.subtract(fee), wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        assertEquals("Pending tx amount is incorrect", amount2.subtract(fee), totalPendingTxAmount);
        try {
            store.close();
        } catch (Exception e) {}
    }

    /**
     * Test that if the block height is missing from coinbase of a version 2
     * block, it's rejected.
     */
    @Test
    public void missingHeightFromCoinbase() throws Exception {

        // TODO
    }
}
