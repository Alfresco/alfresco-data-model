/*
 * #%L
 * Alfresco Data model classes
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.repo.security.authentication;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

import org.alfresco.error.StackTraceUtil;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.service.cmr.repository.datatype.Duration;
import org.alfresco.util.GUID;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.safehaus.uuid.UUIDGenerator;

/**
 * Store tickets in memory. They can be distributed in a cluster via the cache
 * 
 * @author andyh
 */
public class InMemoryTicketComponentImpl implements TicketComponent
{
    /**
     * Ticket prefix
     */
    public static final String GRANTED_AUTHORITY_TICKET_PREFIX = "TICKET_";

    private static Log logger = LogFactory.getLog(InMemoryTicketComponentImpl.class);

    private static ThreadLocal<String> currentTicket = new ThreadLocal<String>();
    private boolean ticketsExpire;
    private Duration validDuration;
    private boolean oneOff;
    private String guid;
    private SimpleCache<String, Ticket> ticketsCache; // Can't use Ticket as it's private
    private ExpiryMode expiryMode = ExpiryMode.AFTER_INACTIVITY;
    private boolean useSingleTicketPerUser = true;

    /**
     * IOC constructor
     */
    public InMemoryTicketComponentImpl()
    {
        super();
        guid = GUID.generate();
    }

    /**
     * Set the ticket cache to support clustering
     */
    public void setTicketsCache(SimpleCache<String, Ticket> ticketsCache)
    {
        this.ticketsCache = ticketsCache;
    }
    
    /**
     * @param useSingleTicketPerUser the useSingleTicketPerUser to set
     */
    public void setUseSingleTicketPerUser(boolean useSingleTicketPerUser)
    {
        this.useSingleTicketPerUser = useSingleTicketPerUser;
    }

    /**
     * @return the useSingleTicketPerUser
     */
    public boolean getUseSingleTicketPerUser()
    {
        return useSingleTicketPerUser;
    }

    /**
     * Are tickets single use
     */
    public void setOneOff(boolean oneOff)
    {
        this.oneOff = oneOff;
    }

    /**
     * Do tickets expire
     */
    public void setTicketsExpire(boolean ticketsExpire)
    {
        this.ticketsExpire = ticketsExpire;
    }

    /**
     * How should tickets expire.
     */
    public void setExpiryMode(String expiryMode)
    {
        this.expiryMode = ExpiryMode.valueOf(expiryMode);
    }

    /**
     * How long are tickets valid (XML duration as a string)
     */
    public void setValidDuration(String validDuration)
    {
        this.validDuration = new Duration(validDuration);
    }

    @Override
    public String getNewTicket(String userName) throws AuthenticationException
    {
        logger.trace("Requested new ticket for: " + "\nuser: " + userName + "\nuseSingleTicketPerUser: " + useSingleTicketPerUser + "\nticketsExpire: "
            + ticketsExpire);
        Ticket ticket = null;
        if(useSingleTicketPerUser)
        {
             ticket = findNonExpiredUserTicket(userName);
        }
        
        if(ticket == null)
        {
            Date expiryDate = null;
            if (ticketsExpire)
            {
                expiryDate = Duration.add(new Date(), validDuration);
            }
            ticket = new Ticket(ticketsExpire ? expiryMode : ExpiryMode.DO_NOT_EXPIRE, expiryDate, userName, validDuration);
            putTicketIntoCache(ticket);
        }
      
        String ticketString = GRANTED_AUTHORITY_TICKET_PREFIX + ticket.getTicketId();
        currentTicket.set(ticketString);
//        if (logger.isTraceEnabled())
//        {
//            logger.trace("Setting the current ticket for this thread: " + Thread.currentThread().getName() + " to: " + ticketString);
//        }
        return ticketString;
    }

    private Collection<String> getTicketCacheKeys()
    {
        final Collection<String> keys = ticketsCache.getKeys();
        logger.trace("GET ALL Getting all of the keys from the ticketsCache, size: " + keys.size());
        return keys;
    }

    private void putTicketIntoCache(Ticket ticket)
    {
        logger.trace("PUT Putting into ticketsCache " + ticketsCache.toString() +" ticket with id: " + ticket.getTicketId() + " ticket: " + ticket + " for user: " + getUserNameFromTicket(ticket));
        ticketsCache.put(ticket.getTicketId(), ticket);
    }

    private Ticket getTicketFromTicketCache(String ticketKey)
    {
        final Ticket ticket = ticketsCache.get(ticketKey);
        logger.trace("GET Requested ticket for ticket key: " + ticketKey + " was: " + ticket + " for user: " + getUserNameFromTicket(ticket));
        return ticket;
    }
    private void removeFromTicketCache(String ticketKey)
    {
        logger.warn("DELETE Removing from ticketsCache " + ticketsCache.toString() + " ticket key: " + ticketKey);
        StringBuilder sb = new StringBuilder();
        StackTraceUtil
            .buildStackTrace(("Removing from ticketsCache ticket key: " + ticketKey + " \n\n"), Thread.currentThread().getStackTrace(), sb, 0);
        logger.trace(sb);

        ticketsCache.remove(ticketKey);
    }

    private void clearTicketCache()
    {
        logger.error("CLEAR Clearing the entire tickets cache. size before clean: " + getTicketCacheKeys().size());
        ticketsCache.clear();
    }


    private String getUserNameFromTicket(Ticket ticket)
    {
        return (ticket!=null)?ticket.getUserName():"NA";
    }

    private Ticket findNonExpiredUserTicket(String userName)
    {
        for (String key : getTicketCacheKeys())
        {
            Ticket ticket = getTicketFromTicketCache(key);
            if (ticket != null)
            {
                if(ticket.getUserName().equals(userName))
                {
                    Ticket newTicket = ticket.getNewEntry();
                    if(newTicket != null)
                    {
                        if (newTicket != ticket)
                        {
                            putTicketIntoCache(newTicket);
                        }
                        logger.trace("Found non expired ticket for user: " + userName + " ticket: " + ticket);
                        return ticket;
                    }
                }
            }
        }
        logger.trace("Did not find non expired ticket for user: " + userName);
        return null;
    }

    @Override
    public String validateTicket(String ticketString) throws AuthenticationException
    {
        if (logger.isTraceEnabled())
        {
            logger.trace("Validating ticket: " + ticketString);
        }

        Ticket ticket = getTicketFromTicketCache(getTicketKey(ticketString));
        if (ticket == null)
        {
            final String msg = "Missing ticket for " + ticketString;
            if (logger.isDebugEnabled())
            {
                logger.debug(msg);
            }
            ticketsCache.toString();
            throw new AuthenticationException(msg);
        }
        Ticket newTicket = ticket.getNewEntry();
        if (newTicket == null)
        {
            final String msg = "Ticket expired for " + ticketString;
            if (logger.isDebugEnabled())
            {
                logger.debug(msg);
            }
            throw new TicketExpiredException(msg);
        }
        if (oneOff)
        {
            logger.trace("This should never happen. oneOff is deprecated");
            //ticketsCache.remove(ticketKey);
        }
        else if (newTicket != ticket)
        {
            putTicketIntoCache( newTicket);
        }
        currentTicket.set(ticketString);
//        if (logger.isTraceEnabled())
//        {
//            logger.trace("Setting the current ticket for this thread: " + Thread.currentThread().getName() + " to: " + ticketString);
//        }
        logger.trace("Validated user: " + newTicket.getUserName() + " with ticket: " + newTicket);
        return newTicket.getUserName();
    }

    /**
     * Helper method to find a ticket
     * 
     * @param ticketString String
     * @return - the ticket
     */
    private Ticket getTicketByTicketString(String ticketString)
    {
        Ticket ticket = getTicketFromTicketCache(getTicketKey(ticketString));
        return ticket;
    }

    /**
     * Helper method to extract the ticket id from the ticket string
     * 
     * @param ticketString String
     * @return - the ticket key
     */
    private String getTicketKey(String ticketString)
    {
        if (ticketString == null)
        {
            return null;
        }
        else if (ticketString.length() < GRANTED_AUTHORITY_TICKET_PREFIX.length())
        {
            throw new AuthenticationException(ticketString + " is an invalid ticket format");
        }

        String key = ticketString.substring(GRANTED_AUTHORITY_TICKET_PREFIX.length());
        return key;
    }

    @Override
    public void invalidateTicketById(String ticketString)
    {
        String key = ticketString.substring(GRANTED_AUTHORITY_TICKET_PREFIX.length());
        removeFromTicketCache(key);
    }

    @Override
    public Set<String> getUsersWithTickets(boolean nonExpiredOnly)
    {
        Date now = new Date();
        Set<String> users = new HashSet<String>();
        for (String key : getTicketCacheKeys())
        {
            Ticket ticket = getTicketFromTicketCache(key);
            if (ticket != null)
            {
                if ((nonExpiredOnly == false) || !ticket.hasExpired(now))
                {
                    users.add(ticket.getUserName());
                }
            }
        }
        return users;
    }

    @Override
    public int countTickets(boolean nonExpiredOnly)
    {
        Date now = new Date();
        if (nonExpiredOnly)
        {
            int count = 0;
            for (String key : getTicketCacheKeys())
            {
                Ticket ticket = getTicketFromTicketCache(key);
                if (ticket != null && !ticket.hasExpired(now))
                {
                    count++;
                }
            }
            return count;
        }
        else
        {
            return getTicketCacheKeys().size();
        }
    }

    @Override
    public int invalidateTickets(boolean expiredOnly)
    {
        Date now = new Date();
        int count = 0;
        if (!expiredOnly)
        {
            count = getTicketCacheKeys().size();
            clearTicketCache();
        }
        else
        {
            Set<String> toRemove = new HashSet<String>();
            for (String key : getTicketCacheKeys())
            {
                Ticket ticket = getTicketFromTicketCache(key);
                if (ticket == null || ticket.hasExpired(now))
                {
                    count++;
                    toRemove.add(key);
                }
            }
            for (String id : toRemove)
            {
                removeFromTicketCache(id);
            }
        }
        return count;
    }

    @Override
    public void invalidateTicketByUser(String userName)
    {
        Set<String> toRemove = new HashSet<String>();

        for (String key : getTicketCacheKeys())
        {
            Ticket ticket = getTicketFromTicketCache(key);
            // Hack: The getKeys() call might return keys for null marker objects, yielding null values
            if (ticket == null)
            {
                continue;
            }
            if (ticket.getUserName().equals(userName))
            {
                toRemove.add(ticket.getTicketId());
            }
        }

        for (String id : toRemove)
        {
            removeFromTicketCache(id);
        }
    }

    @Override
    public int hashCode()
    {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((guid == null) ? 0 : guid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final InMemoryTicketComponentImpl other = (InMemoryTicketComponentImpl) obj;
        if (guid == null)
        {
            if (other.guid != null)
                return false;
        }
        else if (!guid.equals(other.guid))
            return false;
        return true;
    }

    /**
     * Ticket
     * 
     * @author andyh
     */
    public static class Ticket implements Serializable
    {
        private static final long serialVersionUID = -5904510560161261049L;

        private final ExpiryMode expires;

        private final Date expiryDate;

        private final String userName;

        private final String ticketId;

        private final Duration validDuration;
        
        private final Duration testDuration;


        Ticket(ExpiryMode expires, Date expiryDate, String userName, Duration validDuration)
        {
            logger.trace("Constructing a new ticket for: " + userName + " with expire policy: " + expires + " that expires: " + expiryDate + " and valid Duration: " + validDuration );
            this.expires = expires;
            this.expiryDate = expiryDate;
            this.userName = userName;
            this.validDuration = validDuration;
            this.testDuration = validDuration.divide(2);
            final String guid = UUIDGenerator.getInstance().generateRandomBasedUUID().toString();

            String encode = (expires.toString()) + ((expiryDate == null) ? new Date().toString() : expiryDate.toString()) + userName + guid;
            logger.trace("While constructing new ticket encode is: " + encode);

            MessageDigest digester;
            String ticketId;
            try
            {
                digester = MessageDigest.getInstance("SHA-1");
                ticketId = new String(Hex.encodeHex(digester.digest(encode.getBytes())));
            }
            catch (NoSuchAlgorithmException e)
            {
                e.printStackTrace();
                logger.error("Could not find the SHA-1 algorithm " + e.getMessage(),e);
                try
                {
                    digester = MessageDigest.getInstance("MD5");
                    ticketId = new String(Hex.encodeHex(digester.digest(encode.getBytes())));
                }
                catch (NoSuchAlgorithmException e1)
                {
                    e.printStackTrace();
                    logger.error("Could not find the MD5 algorithm " + e.getMessage(),e);

                    CRC32 crc = new CRC32();
                    crc.update(encode.getBytes());
                    byte[] bytes = new byte[4];
                    long value = crc.getValue();
                    bytes[0] = (byte) (value & 0xFF);
                    value >>>= 4;
                    bytes[1] = (byte) (value & 0xFF);
                    value >>>= 4;
                    bytes[2] = (byte) (value & 0xFF);
                    value >>>= 4;
                    bytes[3] = (byte) (value & 0xFF);
                    ticketId = new String(Hex.encodeHex(bytes));
                }
            }
            logger.trace("While constructing new ticket ticketId is: "+ ticketId);
            this.ticketId = ticketId;
        }

        private Ticket(ExpiryMode expires, Date expiryDate, String userName, Duration validDuration, String ticketId)
        {
            this.expires = expires;
            this.expiryDate = expiryDate;
            this.userName = userName;
            this.validDuration = validDuration;
            Duration tenPercent = validDuration.divide(10);
            this.testDuration = validDuration.subtract(tenPercent);
            this.ticketId = ticketId;
            logger.trace("Constructing (cloning) a new ticket for: " + userName + " with expire policy: " + expires +
                " that expires: " + expiryDate + " and valid Duration: " + validDuration + " and ticketId: " + ticketId );
        }
        
        boolean hasExpired(Date now)
        {
            return ((expiryDate != null) && (expiryDate.compareTo(now) < 0));
        }

        Ticket getNewEntry()
        {
            logger.trace("getting a new entry");
            switch (expires)
            {
            case AFTER_FIXED_TIME:
                logger.trace("getting a new entry, AFTER_FIXED_TIME");
                if (hasExpired(new Date()))
                {
                    return null;
                }
                else
                {
                    return this;
                }

            case AFTER_INACTIVITY:
                logger.trace("getting a new entry, AFTER_INACTIVITY");
                Date now = new Date();
                if (hasExpired(now))
                {
                    return null;
                }
                else
                {
                    Duration remaining = new Duration(now, expiryDate);
                    if(remaining.compareTo(testDuration) < 0)
                    {
                        return new Ticket(expires, Duration.add(now, validDuration), userName, validDuration, ticketId);
                    }
                    else
                    {
                        return this;
                    }
                }

            case DO_NOT_EXPIRE:
            default:
                logger.trace("getting a new entry, DO_NOT_EXPIRE");
                return this;
            }
        }

        public boolean equals(Object o)
        {
            if (o == this)
            {
                return true;
            }
            if (!(o instanceof Ticket))
            {
                return false;
            }
            Ticket t = (Ticket) o;

            final boolean equal = (this.expires == t.expires) && /*this.expiryDate.equals(t.expiryDate) &&*/
                                   this.userName.equals(t.userName) &&
                                   this.ticketId.equals(t.ticketId);

            logger.trace("Comparing this: " + this  + " with: " + t + "\n\n are equal: " + equal);

            return equal;
        }

        public int hashCode()
        {
            return ticketId.hashCode();
        }

        protected ExpiryMode getExpires()
        {
            return expires;
        }

        protected Date getExpiryDate()
        {
            return expiryDate;
        }

        protected String getTicketId()
        {
            return ticketId;
        }

        protected String getUserName()
        {
            return userName;
        }

        @Override
        public String toString()
        {
            return "<< ticket for: \"" + userName + "\" with expire policy: " + expires +
            " expires: " + expiryDate + " duration: " + validDuration + " and ticketId " + ticketId+" >>";
        }
    }

    @Override
    public String getAuthorityForTicket(String ticketString)
    {
        Ticket ticket = getTicketByTicketString(ticketString);
        if (ticket == null)
        {
            return null;
        }
        return ticket.getUserName();
    }

    @Override
    public String getCurrentTicket(String userName, boolean autoCreate)
    {
        String ticket = currentTicket.get();
        if (ticket == null)
        {
            logger.trace("requested current ticket for: " + userName + " is null. Autocreate: " + autoCreate);
            return autoCreate ? getNewTicket(userName) : null;
        }
        String ticketUser = getAuthorityForTicket(ticket);
        if (userName.equals(ticketUser))
        {
            logger.trace("requested current ticket for: " + userName + " matches currentTicket: " + ticket );
            return ticket;
        }
        else
        {
            logger.trace("requested current ticket for: " + userName + " does not matches currentTicket: " + ticket + " it matches user: " + ticketUser
                + " and autocreate: " + autoCreate);
            return autoCreate ? getNewTicket(userName) : null;
        }
    }

    public void clearCurrentTicket()
    {
        clearCurrentSecurityContext();
    }

    public static void clearCurrentSecurityContext()
    {
        String prevTicket = currentTicket.get();
        currentTicket.set(null);
//        if (logger.isTraceEnabled())
//        {
//            logger.trace("Clearing the current ticket for this thread: " + Thread.currentThread().getName() + " . Previous ticket was: " + prevTicket);
//        }
    }

    public enum ExpiryMode
    {
        AFTER_INACTIVITY, AFTER_FIXED_TIME, DO_NOT_EXPIRE;
    }
}
